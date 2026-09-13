package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.file.service.FileStorageService;
import com.butingbe.domain.reward.entity.BaseRewardPayout;
import com.butingbe.domain.reward.repository.BaseRewardPayoutRepository;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.request.ReviewApproveReqDto;
import com.butingbe.domain.zoneevent.dto.request.ReviewRejectReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewDecisionResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewDetailResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewQueueItemResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewQueuePageResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminSubmissionDetailResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventSubmission;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventSubmissionRepository;
import com.butingbe.domain.zonetitle.dto.response.EquippedTitleResDto;
import com.butingbe.domain.zonetitle.service.ZoneTitleService;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 사진 인증 검수: 큐 조회, 상세 조회, 제출 단위 승인/반려. */
@Service
@RequiredArgsConstructor
public class AdminZoneEventReviewService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;
  private static final String APPROVE_ENDPOINT = "zone-event-review-approve";
  private static final String REJECT_ENDPOINT = "zone-event-review-reject";

  private final ZoneEventParticipationRepository participationRepository;
  private final OperatorAuthorization operatorAuthorization;
  private final ZoneEventSubmissionRepository submissionRepository;
  private final UserRepository userRepository;
  private final FileStorageService fileStorageService;
  private final ZoneTitleService zoneTitleService;
  private final IdempotencyService idempotencyService;
  private final ObjectMapper objectMapper;
  private final BaseRewardPayoutRepository baseRewardPayoutRepository;
  private final ZoneEventReportRepository reportRepository;
  private final ZoneEventAuditLogRepository auditLogRepository;

  /** 검수 큐: UNDER_REVIEW 참여만, roundId/eventId/zoneId로 필터링, joinedAt 오름차순(먼저 온 순). */
  @Transactional(readOnly = true)
  public AdminReviewQueuePageResDto queue(
      AuthenticatedUser user,
      UUID roundId,
      UUID eventId,
      String zoneId,
      Integer page,
      Integer size) {
    operatorAuthorization.requireOperator(user);
    int pageNumber = page == null || page < 1 ? 1 : page;
    int pageSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    String resolvedZoneId =
        zoneId == null || zoneId.isBlank() ? null : ChatZone.fromString(zoneId).name();

    Specification<ZoneEventParticipation> spec = buildQueueSpec(roundId, eventId, resolvedZoneId);
    Page<ZoneEventParticipation> result =
        participationRepository.findAll(
            spec, PageRequest.of(pageNumber - 1, pageSize, Sort.by(Sort.Order.asc("joinedAt"))));

    List<AdminReviewQueueItemResDto> items =
        result.getContent().stream().map(AdminReviewQueueItemResDto::of).toList();
    return new AdminReviewQueuePageResDto(
        items,
        pageNumber,
        pageSize,
        result.getTotalElements(),
        result.getTotalPages(),
        pageNumber < result.getTotalPages());
  }

  /** 검수 상세: 사용자 표시 정보 + 현재 제출 + 전체 제출 이력(attemptNo 내림차순) + 당시 타겟/보상 스냅샷. */
  @Transactional(readOnly = true)
  public AdminReviewDetailResDto detail(AuthenticatedUser user, UUID participationId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventParticipation participation =
        participationRepository
            .findById(participationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.participation.not_found"));
    User participant =
        userRepository
            .findById(participation.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("error.user.not_found"));

    List<ZoneEventSubmission> history =
        submissionRepository.findByParticipation_IdOrderByAttemptNoDesc(participationId);
    List<AdminSubmissionDetailResDto> historyDtos =
        history.stream()
            .map(s -> AdminSubmissionDetailResDto.of(s, presignedUrl(s.getMediaFileKey())))
            .toList();
    AdminSubmissionDetailResDto current =
        historyDtos.stream()
            .filter(
                s ->
                    participation.getCurrentSubmissionId() != null
                        && s.submissionId()
                            .equals(participation.getCurrentSubmissionId().toString()))
            .findFirst()
            .orElse(historyDtos.isEmpty() ? null : historyDtos.get(0));

    return AdminReviewDetailResDto.of(
        participation, participant.getNickname(), participant.getEmail(), current, historyDtos);
  }

  /** 제출 단위 승인: SUCCESS·앨범 공개만 하고 보상은 지급하지 않는다. 칭호 누적 집계는 트리거하되 자동 장착은 하지 않는다. */
  @Transactional
  public AdminReviewDecisionResDto approve(
      AuthenticatedUser user,
      UUID participationId,
      ReviewApproveReqDto request,
      String idempotencyKey) {
    operatorAuthorization.requireOperator(user);
    String fingerprint =
        participationId + ":" + request.submissionId() + ":" + request.expectedRevision();
    Optional<String> replay =
        idempotencyService.findReplay(idempotencyKey, APPROVE_ENDPOINT, fingerprint);
    if (replay.isPresent()) {
      return readJson(replay.get(), AdminReviewDecisionResDto.class);
    }

    ZoneEventParticipation participation = requireUnderReview(participationId);
    ZoneEventSubmission submission =
        requireCurrentSubmission(participation, request.submissionId());
    if (!submission.getRevision().equals(request.expectedRevision())) {
      throw new ConflictException("error.zone_event.review.stale_revision");
    }

    participation.stampReview(user.id());
    participation.markSuccess();
    createBaseRewardPayoutIfNeeded(participation);
    submission.approve(user.id());
    flushSubmission(submission);

    List<EquippedTitleResDto> titles =
        new ArrayList<>(
            zoneTitleService.awardTitles(
                participation.getUserId(), participation.getEvent().getZoneId(), false));
    AdminReviewDecisionResDto result =
        AdminReviewDecisionResDto.of(participation, submission, titles);
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("submissionId", submission.getId().toString());
    detail.put("before", Map.of("status", "UNDER_REVIEW"));
    detail.put("after", Map.of("status", "SUCCESS"));
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action("APPROVE_SUBMISSION")
            .targetType("PARTICIPATION")
            .targetId(participationId)
            .detail(detail)
            .build());
    idempotencyService.save(idempotencyKey, APPROVE_ENDPOINT, fingerprint, result);
    return result;
  }

  /** 제출 단위 반려: 같은 참여 건은 재제출로 재시도할 수 있다. */
  @Transactional
  public void reject(
      AuthenticatedUser user,
      UUID participationId,
      ReviewRejectReqDto request,
      String idempotencyKey) {
    operatorAuthorization.requireOperator(user);
    String fingerprint =
        participationId
            + ":"
            + request.submissionId()
            + ":"
            + request.expectedRevision()
            + ":"
            + request.reason();
    if (idempotencyService.findReplay(idempotencyKey, REJECT_ENDPOINT, fingerprint).isPresent()) {
      return;
    }

    ZoneEventParticipation participation = requireUnderReview(participationId);
    ZoneEventSubmission submission =
        requireCurrentSubmission(participation, request.submissionId());
    if (!submission.getRevision().equals(request.expectedRevision())) {
      throw new ConflictException("error.zone_event.review.stale_revision");
    }

    participation.stampReview(user.id());
    participation.markFail(request.reason());
    submission.reject(user.id(), request.reason());
    flushSubmission(submission);

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("submissionId", submission.getId().toString());
    detail.put("reason", request.reason());
    detail.put("before", Map.of("status", "UNDER_REVIEW"));
    detail.put("after", Map.of("status", "FAIL"));
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action("REJECT_SUBMISSION")
            .targetType("PARTICIPATION")
            .targetId(participationId)
            .detail(detail)
            .build());
    idempotencyService.save(idempotencyKey, REJECT_ENDPOINT, fingerprint, null);
  }

  private ZoneEventParticipation requireUnderReview(UUID participationId) {
    ZoneEventParticipation participation =
        participationRepository
            .findById(participationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.participation.not_found"));
    if (participation.getStatus() != ParticipationStatus.UNDER_REVIEW) {
      throw new ConflictException("error.zone_event.participation.invalid_state");
    }
    return participation;
  }

  private ZoneEventSubmission requireCurrentSubmission(
      ZoneEventParticipation participation, UUID submissionId) {
    ZoneEventSubmission submission =
        submissionRepository
            .findById(submissionId)
            .filter(s -> s.getParticipation().getId().equals(participation.getId()))
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.submission.not_found"));
    if (!submission.getId().equals(participation.getCurrentSubmissionId())) {
      throw new ConflictException("error.zone_event.review.stale_submission");
    }
    return submission;
  }

  private void flushSubmission(ZoneEventSubmission submission) {
    try {
      submissionRepository.saveAndFlush(submission);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ConflictException("error.zone_event.review.stale_revision");
    }
  }

  /** 이벤트에 baseReward가 있고 아직 이 참여의 BASE 지급 건이 없으면 PENDING_CONFIRM으로 생성한다. 미해결 신고가 있으면 즉시 보류. */
  private void createBaseRewardPayoutIfNeeded(ZoneEventParticipation participation) {
    var baseReward = participation.getEvent().getBaseReward();
    if (baseReward == null) {
      return;
    }
    if (baseRewardPayoutRepository.findByParticipationId(participation.getId()).isPresent()) {
      return;
    }
    BaseRewardPayout payout =
        BaseRewardPayout.builder()
            .participationId(participation.getId())
            .reward(baseReward)
            .build();
    if (reportRepository.hasUnresolvedReports(participation.getId())) {
      payout.hold();
    }
    baseRewardPayoutRepository.save(payout);
  }

  private <T> T readJson(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JacksonException e) {
      throw new IllegalStateException("Failed to deserialize idempotent response.", e);
    }
  }

  private String presignedUrl(String mediaFileKey) {
    return mediaFileKey == null ? null : fileStorageService.getPresignedUrl(mediaFileKey);
  }

  private Specification<ZoneEventParticipation> buildQueueSpec(
      UUID roundId, UUID eventId, String zoneId) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("status"), ParticipationStatus.UNDER_REVIEW));
      if (roundId != null) {
        predicates.add(cb.equal(root.get("event").get("roundId"), roundId));
      }
      if (eventId != null) {
        predicates.add(cb.equal(root.get("event").get("id"), eventId));
      }
      if (zoneId != null) {
        predicates.add(cb.equal(root.get("event").get("zoneId"), zoneId));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
