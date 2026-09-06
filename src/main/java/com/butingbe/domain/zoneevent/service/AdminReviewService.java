package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.reward.dto.response.BaseRewardResult;
import com.butingbe.domain.reward.service.RewardRevokeService;
import com.butingbe.domain.reward.service.RewardService;
import com.butingbe.domain.zoneevent.dto.response.ParticipationResDto;
import com.butingbe.domain.zoneevent.dto.response.ReviewQueueItemResDto;
import com.butingbe.domain.zoneevent.dto.response.ReviewQueuePageResDto;
import com.butingbe.domain.zoneevent.dto.response.SubmitResultResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ReportStatus;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.domain.zonetitle.service.ZoneTitleService;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 검수: 검수 대기·신고 누적 참여를 승인·반려·회수·숨김 해제한다.
 *
 * <p>승인·회수의 실제 보상·칭호 처리는 제출·정산에서 쓰는 서비스를 그대로 재사용한다. 회수로 누적 성공 수가 줄어도 칭호는 회수하지 않으며(FR-TTL-07), 진행도는
 * 조회 시 다시 계산되므로 별도 재계산 작업이 없다.
 */
@Service
@RequiredArgsConstructor
public class AdminReviewService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  private final ZoneEventParticipationRepository participationRepository;
  private final ZoneEventReportRepository reportRepository;
  private final RewardService rewardService;
  private final RewardRevokeService rewardRevokeService;
  private final ZoneTitleService zoneTitleService;
  private final OperatorAuthorization operatorAuthorization;

  @Transactional(readOnly = true)
  public ReviewQueuePageResDto reviewQueue(AuthenticatedUser user, String cursor, Integer size) {
    operatorAuthorization.requireOperator(user);
    int pageSize = resolveSize(size);
    Cursor decoded = decodeCursor(cursor);

    Specification<ZoneEventParticipation> spec =
        (root, query, cb) -> {
          Predicate needsReview =
              cb.or(
                  cb.equal(root.get("status"), ParticipationStatus.UNDER_REVIEW),
                  cb.isTrue(root.get("hidden")));
          if (decoded == null) {
            return needsReview;
          }
          Predicate earlier = cb.lessThan(root.get("joinedAt"), decoded.joinedAt());
          Predicate sameTimeLowerId =
              cb.and(
                  cb.equal(root.get("joinedAt"), decoded.joinedAt()),
                  cb.lessThan(root.get("id"), decoded.id()));
          return cb.and(needsReview, cb.or(earlier, sameTimeLowerId));
        };
    List<ZoneEventParticipation> rows =
        participationRepository
            .findAll(
                spec,
                PageRequest.of(
                    0, pageSize + 1, Sort.by(Sort.Order.desc("joinedAt"), Sort.Order.desc("id"))))
            .getContent();

    boolean hasNext = rows.size() > pageSize;
    List<ZoneEventParticipation> page = hasNext ? rows.subList(0, pageSize) : rows;
    List<ReviewQueueItemResDto> items =
        page.stream()
            .map(
                p ->
                    ReviewQueueItemResDto.of(p, reportRepository.countByParticipationId(p.getId())))
            .toList();
    String nextCursor = hasNext ? encodeCursor(page.get(page.size() - 1)) : null;
    return new ReviewQueuePageResDto(items, nextCursor, hasNext);
  }

  /** UNDER_REVIEW → SUCCESS + 보상·칭호 지급(제출 성공 경로와 동일). */
  @Transactional
  public SubmitResultResDto approve(AuthenticatedUser user, UUID participationId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventParticipation participation =
        requireStatus(participationId, ParticipationStatus.UNDER_REVIEW);
    participation.stampReview(user.id());
    participation.markSuccess();

    ZoneEvent event = participation.getEvent();
    RewardSnapshot base = event.getBaseReward();
    BaseRewardResult reward =
        rewardService.grantBaseReward(
            participation.getUserId(),
            participationId,
            event.getId(),
            base == null ? null : base.points(),
            base == null ? null : base.badgeCode());
    List<Object> titles =
        new ArrayList<>(zoneTitleService.awardTitles(participation.getUserId(), event.getZoneId()));
    return SubmitResultResDto.of(
        ParticipationResDto.of(participation, null),
        reward.rewards(),
        reward.pointBalance(),
        titles);
  }

  /** UNDER_REVIEW → FAIL. */
  @Transactional
  public void reject(AuthenticatedUser user, UUID participationId, String failReason) {
    operatorAuthorization.requireOperator(user);
    ZoneEventParticipation participation =
        requireStatus(participationId, ParticipationStatus.UNDER_REVIEW);
    participation.stampReview(user.id());
    participation.markFail(failReason);
  }

  /** SUCCESS → REVOKED + 보상 회수(포인트 되돌림, 미사용 쿠폰 회수). */
  @Transactional
  public void revoke(AuthenticatedUser user, UUID participationId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventParticipation participation =
        requireStatus(participationId, ParticipationStatus.SUCCESS);
    participation.stampReview(user.id());
    participation.markRevoked();
    rewardRevokeService.revokeParticipationRewards(participationId);
  }

  /** 신고 자동 숨김 해제 + 신고 DISMISSED. */
  @Transactional
  public void unhide(AuthenticatedUser user, UUID participationId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventParticipation participation =
        participationRepository
            .findById(participationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.participation.not_found"));
    participation.unhide();
    for (ZoneEventReport report : reportRepository.findByParticipationId(participationId)) {
      report.resolveAs(ReportStatus.DISMISSED);
    }
  }

  private ZoneEventParticipation requireStatus(UUID participationId, ParticipationStatus expected) {
    ZoneEventParticipation participation =
        participationRepository
            .findById(participationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.participation.not_found"));
    if (participation.getStatus() != expected) {
      throw new ConflictException("error.zone_event.participation.invalid_state");
    }
    return participation;
  }

  private int resolveSize(Integer size) {
    if (size == null || size <= 0) {
      return DEFAULT_SIZE;
    }
    return Math.min(size, MAX_SIZE);
  }

  private String encodeCursor(ZoneEventParticipation p) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString((p.getJoinedAt() + "|" + p.getId()).getBytes(StandardCharsets.UTF_8));
  }

  private Cursor decodeCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String[] parts =
          new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|");
      if (parts.length != 2) {
        throw new IllegalArgumentException("Invalid review cursor.");
      }
      return new Cursor(OffsetDateTime.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
      throw new IllegalArgumentException("Invalid review cursor.");
    }
  }

  private record Cursor(OffsetDateTime joinedAt, UUID id) {}
}
