package com.butingbe.domain.reward.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.reward.dto.request.ReleaseHoldReqDto;
import com.butingbe.domain.reward.dto.response.AdminRewardPayoutDetailResDto;
import com.butingbe.domain.reward.dto.response.AdminRewardPayoutReleaseHoldResDto;
import com.butingbe.domain.reward.entity.BaseRewardPayout;
import com.butingbe.domain.reward.entity.PayoutHoldStatus;
import com.butingbe.domain.reward.entity.RewardPayout;
import com.butingbe.domain.reward.repository.BaseRewardPayoutRepository;
import com.butingbe.domain.reward.repository.RewardPayoutRepository;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.domain.zoneevent.service.IdempotencyService;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 신고로 보류된 지급의 해제. 미해결 신고가 하나도 없어야 하며, 이 신고 검수 흐름에서 보류를 해제하는 API다(issue #243).
 *
 * <p>참여 숨김 해제({@code AdminReviewService.unhide()})는 별도의 레거시 경로로 issue #241부터 존재하며, 참여의 모든 신고를 일괄
 * 기각하는 부수 효과로 보류도 해제한다. 이번 이슈 범위 밖이라 손대지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AdminRewardPayoutService {

  private static final String RELEASE_HOLD_ENDPOINT = "reward-payout-release-hold";

  private final OperatorAuthorization operatorAuthorization;
  private final RewardPayoutRepository rewardPayoutRepository;
  private final BaseRewardPayoutRepository baseRewardPayoutRepository;
  private final ZoneEventReportRepository reportRepository;
  private final ZoneEventAuditLogRepository auditLogRepository;
  private final IdempotencyService idempotencyService;
  private final ObjectMapper objectMapper;
  private final ZoneEventParticipationRepository participationRepository;

  @Transactional(readOnly = true)
  public AdminRewardPayoutDetailResDto detail(AuthenticatedUser user, UUID payoutId) {
    operatorAuthorization.requireOperator(user);
    Optional<RewardPayout> topLike = rewardPayoutRepository.findById(payoutId);
    if (topLike.isPresent()) {
      return AdminRewardPayoutDetailResDto.ofTopLike(topLike.get());
    }
    BaseRewardPayout base =
        baseRewardPayoutRepository
            .findById(payoutId)
            .orElseThrow(() -> new ResourceNotFoundException("error.reward.payout.not_found"));
    UUID eventId =
        participationRepository
            .findById(base.getParticipationId())
            .map(ZoneEventParticipation::getEvent)
            .map(com.butingbe.domain.zoneevent.entity.ZoneEvent::getId)
            .orElse(null);
    return AdminRewardPayoutDetailResDto.ofBase(base, eventId);
  }

  @Transactional
  public AdminRewardPayoutReleaseHoldResDto releaseHold(
      AuthenticatedUser user, UUID payoutId, ReleaseHoldReqDto request, String idempotencyKey) {
    operatorAuthorization.requireOperator(user);
    String fingerprint = payoutId + ":" + request.note() + ":" + request.expectedRevision();
    Optional<String> replay =
        idempotencyService.findReplay(idempotencyKey, RELEASE_HOLD_ENDPOINT, fingerprint);
    if (replay.isPresent()) {
      return readJson(replay.get(), AdminRewardPayoutReleaseHoldResDto.class);
    }

    AdminRewardPayoutReleaseHoldResDto result;
    Optional<RewardPayout> topLike = rewardPayoutRepository.findById(payoutId);
    if (topLike.isPresent()) {
      result = releaseTopLikeHold(topLike.get(), request);
    } else {
      BaseRewardPayout base =
          baseRewardPayoutRepository
              .findById(payoutId)
              .orElseThrow(() -> new ResourceNotFoundException("error.reward.payout.not_found"));
      result = releaseBaseHold(base, request);
    }

    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action("RELEASE_PAYOUT_HOLD")
            .targetType("REWARD_PAYOUT")
            .targetId(payoutId)
            .detail(Map.of("note", request.note(), "payoutType", result.payoutType()))
            .build());
    idempotencyService.save(idempotencyKey, RELEASE_HOLD_ENDPOINT, fingerprint, result);
    return result;
  }

  private AdminRewardPayoutReleaseHoldResDto releaseTopLikeHold(
      RewardPayout payout, ReleaseHoldReqDto request) {
    requireHeldAndCurrent(
        payout.getHoldStatus(), payout.getRevision(), payout.getParticipationId(), request);
    payout.releaseHold();
    try {
      rewardPayoutRepository.saveAndFlush(payout);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    return new AdminRewardPayoutReleaseHoldResDto(
        payout.getId().toString(), "TOP_LIKE", payout.getHoldStatus().name(), payout.getRevision());
  }

  private AdminRewardPayoutReleaseHoldResDto releaseBaseHold(
      BaseRewardPayout payout, ReleaseHoldReqDto request) {
    requireHeldAndCurrent(
        payout.getHoldStatus(), payout.getRevision(), payout.getParticipationId(), request);
    payout.releaseHold();
    try {
      baseRewardPayoutRepository.saveAndFlush(payout);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    return new AdminRewardPayoutReleaseHoldResDto(
        payout.getId().toString(), "BASE", payout.getHoldStatus().name(), payout.getRevision());
  }

  /**
   * revision·보류 상태·미해결 신고 전체를 확인한다 — 방금 처리한 신고 하나만 보고 판단하지 않는다. (레거시 {@code
   * AdminReviewService.unhide()} 경로는 예외로 남아 있다 — issue #241, 이번 이슈 범위 밖.)
   */
  private void requireHeldAndCurrent(
      PayoutHoldStatus holdStatus, Long revision, UUID participationId, ReleaseHoldReqDto request) {
    if (!revision.equals(request.expectedRevision())) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    if (holdStatus != PayoutHoldStatus.HELD_REPORT) {
      throw new ConflictException("error.reward.payout.not_held");
    }
    if (reportRepository.hasUnresolvedReports(participationId)) {
      throw new ConflictException("error.reward.payout.unresolved_reports_remain");
    }
  }

  private <T> T readJson(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JacksonException e) {
      throw new IllegalStateException("Failed to deserialize idempotent response.", e);
    }
  }
}
