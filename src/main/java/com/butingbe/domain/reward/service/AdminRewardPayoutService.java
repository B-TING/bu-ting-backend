package com.butingbe.domain.reward.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.reward.dto.request.AdminRewardPayoutBulkConfirmReqDto;
import com.butingbe.domain.reward.dto.request.AdminRewardPayoutBulkScheduleReqDto;
import com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkReqDto;
import com.butingbe.domain.reward.dto.request.AdminRewardPayoutMarkSentReqDto;
import com.butingbe.domain.reward.dto.request.AdminRewardPayoutRetryReqDto;
import com.butingbe.domain.reward.dto.request.AdminRewardPayoutUpdateReqDto;
import com.butingbe.domain.reward.dto.request.ReleaseHoldReqDto;
import com.butingbe.domain.reward.dto.response.AdminRewardPayoutBulkResultResDto;
import com.butingbe.domain.reward.dto.response.AdminRewardPayoutDetailResDto;
import com.butingbe.domain.reward.dto.response.AdminRewardPayoutListItemResDto;
import com.butingbe.domain.reward.dto.response.AdminRewardPayoutPageResDto;
import com.butingbe.domain.reward.dto.response.AdminRewardPayoutReleaseHoldResDto;
import com.butingbe.domain.reward.entity.BaseRewardPayout;
import com.butingbe.domain.reward.entity.BaseRewardPayoutStatus;
import com.butingbe.domain.reward.entity.PayoutHoldStatus;
import com.butingbe.domain.reward.entity.RewardPayout;
import com.butingbe.domain.reward.entity.RewardPayoutStatus;
import com.butingbe.domain.reward.repository.BaseRewardPayoutRepository;
import com.butingbe.domain.reward.repository.RewardPayoutRepository;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.domain.zoneevent.service.IdempotencyService;
import com.butingbe.global.error.exception.BulkPayoutConflictException;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
  private static final String UPDATE_ENDPOINT = "reward-payout-update";
  private static final String BULK_CONFIRM_ENDPOINT = "reward-payout-bulk-confirm";
  private static final String BULK_SCHEDULE_ENDPOINT = "reward-payout-bulk-schedule";
  private static final String MARK_MAIL_SENT_ENDPOINT = "reward-payout-mark-mail-sent";
  private static final String MARK_INFO_COLLECTED_ENDPOINT = "reward-payout-mark-info-collected";
  private static final String MARK_SENT_ENDPOINT = "reward-payout-mark-sent";
  private static final String RETRY_ENDPOINT = "reward-payout-retry";
  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  private final OperatorAuthorization operatorAuthorization;
  private final RewardPayoutRepository rewardPayoutRepository;
  private final BaseRewardPayoutRepository baseRewardPayoutRepository;
  private final ZoneEventReportRepository reportRepository;
  private final ZoneEventAuditLogRepository auditLogRepository;
  private final IdempotencyService idempotencyService;
  private final ObjectMapper objectMapper;
  private final ZoneEventParticipationRepository participationRepository;
  private final RewardService rewardService;

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

  @Transactional(readOnly = true)
  public AdminRewardPayoutPageResDto list(
      AuthenticatedUser user,
      UUID roundId,
      UUID eventId,
      String rewardReason,
      String status,
      String holdStatus,
      OffsetDateTime scheduledFrom,
      OffsetDateTime scheduledTo,
      Integer page,
      Integer size) {
    operatorAuthorization.requireOperator(user);
    if (status != null && !status.isBlank() && (rewardReason == null || rewardReason.isBlank())) {
      throw new IllegalArgumentException("error.reward.payout.status_requires_reward_reason");
    }
    int pageNumber = page == null || page < 1 ? 1 : page;
    int pageSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    PayoutHoldStatus holdFilter =
        holdStatus == null || holdStatus.isBlank() ? null : PayoutHoldStatus.valueOf(holdStatus);

    if ("BASE".equals(rewardReason)) {
      BaseRewardPayoutStatus statusFilter =
          status == null || status.isBlank() ? null : BaseRewardPayoutStatus.valueOf(status);
      Page<BaseRewardPayout> result =
          baseRewardPayoutRepository.searchForAdmin(
              eventId,
              roundId,
              statusFilter,
              holdFilter,
              scheduledFrom,
              scheduledTo,
              PageRequest.of(pageNumber - 1, pageSize));
      List<AdminRewardPayoutListItemResDto> items = mapBaseItems(result.getContent());
      return new AdminRewardPayoutPageResDto(
          items,
          pageNumber,
          pageSize,
          result.getTotalElements(),
          result.getTotalPages(),
          pageNumber < result.getTotalPages());
    }
    if ("TOP_LIKE".equals(rewardReason)) {
      RewardPayoutStatus statusFilter =
          status == null || status.isBlank() ? null : RewardPayoutStatus.valueOf(status);
      Page<RewardPayout> result =
          rewardPayoutRepository.searchForAdmin(
              eventId,
              roundId,
              statusFilter,
              holdFilter,
              scheduledFrom,
              scheduledTo,
              PageRequest.of(pageNumber - 1, pageSize));
      List<AdminRewardPayoutListItemResDto> items =
          result.getContent().stream().map(AdminRewardPayoutListItemResDto::ofTopLike).toList();
      return new AdminRewardPayoutPageResDto(
          items,
          pageNumber,
          pageSize,
          result.getTotalElements(),
          result.getTotalPages(),
          pageNumber < result.getTotalPages());
    }

    // rewardReason 미지정: 두 리포지토리를 각각 무페이징 조회 후 병합·정렬·인메모리 페이징.
    List<RewardPayout> topLikeAll =
        rewardPayoutRepository
            .searchForAdmin(
                eventId,
                roundId,
                null,
                holdFilter,
                scheduledFrom,
                scheduledTo,
                org.springframework.data.domain.Pageable.unpaged())
            .getContent();
    List<BaseRewardPayout> baseAll =
        baseRewardPayoutRepository
            .searchForAdmin(
                eventId,
                roundId,
                null,
                holdFilter,
                scheduledFrom,
                scheduledTo,
                org.springframework.data.domain.Pageable.unpaged())
            .getContent();
    List<AdminRewardPayoutListItemResDto> merged = new ArrayList<>();
    merged.addAll(topLikeAll.stream().map(AdminRewardPayoutListItemResDto::ofTopLike).toList());
    merged.addAll(mapBaseItems(baseAll));
    merged.sort(
        Comparator.comparing(
                AdminRewardPayoutListItemResDto::scheduledAt,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed());

    int totalElements = merged.size();
    int totalPages = Math.max(1, (int) Math.ceil(totalElements / (double) pageSize));
    int fromIndex = Math.min((pageNumber - 1) * pageSize, totalElements);
    int toIndex = Math.min(fromIndex + pageSize, totalElements);
    List<AdminRewardPayoutListItemResDto> pageItems = merged.subList(fromIndex, toIndex);
    return new AdminRewardPayoutPageResDto(
        pageItems, pageNumber, pageSize, totalElements, totalPages, pageNumber < totalPages);
  }

  private List<AdminRewardPayoutListItemResDto> mapBaseItems(List<BaseRewardPayout> rows) {
    var participations =
        participationRepository
            .findAllById(
                rows.stream().map(BaseRewardPayout::getParticipationId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(ZoneEventParticipation::getId, Function.identity()));
    return rows.stream()
        .map(
            p -> {
              ZoneEventParticipation participation = participations.get(p.getParticipationId());
              UUID eventId = participation == null ? null : participation.getEvent().getId();
              return AdminRewardPayoutListItemResDto.ofBase(p, eventId);
            })
        .toList();
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

  @Transactional
  public AdminRewardPayoutDetailResDto update(
      AuthenticatedUser user,
      UUID payoutId,
      AdminRewardPayoutUpdateReqDto request,
      String idempotencyKey) {
    operatorAuthorization.requireOperator(user);
    String fingerprint =
        payoutId
            + ":"
            + request.reward()
            + ":"
            + request.memo()
            + ":"
            + request.scheduledAt()
            + ":"
            + request.expectedRevision();
    Optional<String> replay =
        idempotencyService.findReplay(idempotencyKey, UPDATE_ENDPOINT, fingerprint);
    if (replay.isPresent()) {
      return readJson(replay.get(), AdminRewardPayoutDetailResDto.class);
    }

    AdminRewardPayoutDetailResDto result;
    Optional<RewardPayout> topLike = rewardPayoutRepository.findById(payoutId);
    if (topLike.isPresent()) {
      result = updateTopLike(topLike.get(), request);
    } else {
      BaseRewardPayout base =
          baseRewardPayoutRepository
              .findById(payoutId)
              .orElseThrow(() -> new ResourceNotFoundException("error.reward.payout.not_found"));
      result = updateBase(base, request);
    }

    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action("PATCH_PAYOUT")
            .targetType("REWARD_PAYOUT")
            .targetId(payoutId)
            .detail(Map.of("payoutType", result.payoutType()))
            .build());
    idempotencyService.save(idempotencyKey, UPDATE_ENDPOINT, fingerprint, result);
    return result;
  }

  private AdminRewardPayoutDetailResDto updateTopLike(
      RewardPayout payout, AdminRewardPayoutUpdateReqDto request) {
    if (!payout.getRevision().equals(request.expectedRevision())) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    if (request.reward() != null
        && payout.getStatus() != RewardPayoutStatus.PENDING_ASSIGN
        && payout.getStatus() != RewardPayoutStatus.PENDING_CONFIRM) {
      throw new ConflictException("error.reward.payout.reward_locked");
    }
    if (request.reward() != null) {
      payout.assignReward(request.reward());
    }
    if (request.memo() != null) {
      payout.updateMemo(request.memo());
    }
    if (request.scheduledAt() != null) {
      payout.updateSchedule(request.scheduledAt());
    }
    try {
      rewardPayoutRepository.saveAndFlush(payout);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    return AdminRewardPayoutDetailResDto.ofTopLike(payout);
  }

  private AdminRewardPayoutDetailResDto updateBase(
      BaseRewardPayout payout, AdminRewardPayoutUpdateReqDto request) {
    if (!payout.getRevision().equals(request.expectedRevision())) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    if (request.reward() != null && payout.getStatus() != BaseRewardPayoutStatus.PENDING_CONFIRM) {
      throw new ConflictException("error.reward.payout.reward_locked");
    }
    if (request.reward() != null) {
      payout.updateReward(request.reward());
    }
    if (request.memo() != null) {
      payout.updateMemo(request.memo());
    }
    if (request.scheduledAt() != null) {
      payout.updateSchedule(request.scheduledAt());
    }
    UUID eventId =
        participationRepository
            .findById(payout.getParticipationId())
            .map(ZoneEventParticipation::getEvent)
            .map(com.butingbe.domain.zoneevent.entity.ZoneEvent::getId)
            .orElse(null);
    try {
      baseRewardPayoutRepository.saveAndFlush(payout);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    return AdminRewardPayoutDetailResDto.ofBase(payout, eventId);
  }

  @Transactional
  public AdminRewardPayoutBulkResultResDto bulkConfirm(
      AuthenticatedUser user, AdminRewardPayoutBulkConfirmReqDto request, String idempotencyKey) {
    operatorAuthorization.requireOperator(user);
    List<UUID> ids = request.payoutIds().stream().map(UUID::fromString).toList();
    String fingerprint = ids + ":" + request.expectedRevisions();
    Optional<String> replay =
        idempotencyService.findReplay(idempotencyKey, BULK_CONFIRM_ENDPOINT, fingerprint);
    if (replay.isPresent()) {
      return readJson(replay.get(), AdminRewardPayoutBulkResultResDto.class);
    }

    List<String> problems = new ArrayList<>();
    for (UUID id : ids) {
      Long expected = request.expectedRevisions().get(id.toString());
      Optional<RewardPayout> topLike = rewardPayoutRepository.findById(id);
      if (topLike.isPresent()) {
        RewardPayout p = topLike.get();
        if (expected == null || !p.getRevision().equals(expected)) {
          problems.add(id.toString());
        } else if (p.getHoldStatus() != PayoutHoldStatus.NONE) {
          problems.add(id.toString());
        } else if (p.getStatus() != RewardPayoutStatus.PENDING_CONFIRM) {
          problems.add(id.toString());
        }
        continue;
      }
      Optional<BaseRewardPayout> base = baseRewardPayoutRepository.findById(id);
      if (base.isEmpty()) {
        problems.add(id.toString());
        continue;
      }
      BaseRewardPayout p = base.get();
      if (expected == null || !p.getRevision().equals(expected)) {
        problems.add(id.toString());
      } else if (p.getHoldStatus() != PayoutHoldStatus.NONE) {
        problems.add(id.toString());
      } else if (p.getStatus() != BaseRewardPayoutStatus.PENDING_CONFIRM) {
        problems.add(id.toString());
      }
    }
    if (!problems.isEmpty()) {
      throw new BulkPayoutConflictException("error.reward.payout.bulk_conflict", problems);
    }

    for (UUID id : ids) {
      rewardPayoutRepository
          .findById(id)
          .ifPresentOrElse(
              p -> {
                p.confirm(user.id());
                auditLogRepository.save(
                    ZoneEventAuditLog.builder()
                        .actorId(user.id())
                        .action("CONFIRM_PAYOUT")
                        .targetType("REWARD_PAYOUT")
                        .targetId(id)
                        .detail(Map.of("payoutType", "TOP_LIKE"))
                        .build());
              },
              () ->
                  baseRewardPayoutRepository
                      .findById(id)
                      .ifPresent(
                          p -> {
                            p.confirm(user.id());
                            auditLogRepository.save(
                                ZoneEventAuditLog.builder()
                                    .actorId(user.id())
                                    .action("CONFIRM_PAYOUT")
                                    .targetType("REWARD_PAYOUT")
                                    .targetId(id)
                                    .detail(Map.of("payoutType", "BASE"))
                                    .build());
                          }));
    }

    AdminRewardPayoutBulkResultResDto result =
        new AdminRewardPayoutBulkResultResDto(ids.stream().map(UUID::toString).toList());
    idempotencyService.save(idempotencyKey, BULK_CONFIRM_ENDPOINT, fingerprint, result);
    return result;
  }

  @Transactional
  public AdminRewardPayoutBulkResultResDto bulkSchedule(
      AuthenticatedUser user, AdminRewardPayoutBulkScheduleReqDto request, String idempotencyKey) {
    operatorAuthorization.requireOperator(user);
    List<UUID> ids = request.payoutIds().stream().map(UUID::fromString).toList();
    String fingerprint = ids + ":" + request.scheduledAt() + ":" + request.expectedRevisions();
    Optional<String> replay =
        idempotencyService.findReplay(idempotencyKey, BULK_SCHEDULE_ENDPOINT, fingerprint);
    if (replay.isPresent()) {
      return readJson(replay.get(), AdminRewardPayoutBulkResultResDto.class);
    }

    List<String> problems = new ArrayList<>();
    for (UUID id : ids) {
      Long expected = request.expectedRevisions().get(id.toString());
      Optional<RewardPayout> topLike = rewardPayoutRepository.findById(id);
      if (topLike.isPresent()) {
        RewardPayout p = topLike.get();
        if (expected == null || !p.getRevision().equals(expected)) {
          problems.add(id.toString());
        } else if (p.getHoldStatus() != PayoutHoldStatus.NONE) {
          problems.add(id.toString());
        } else if (p.getStatus() != RewardPayoutStatus.CONFIRMED) {
          problems.add(id.toString());
        }
        continue;
      }
      Optional<BaseRewardPayout> base = baseRewardPayoutRepository.findById(id);
      if (base.isEmpty()) {
        problems.add(id.toString());
        continue;
      }
      BaseRewardPayout p = base.get();
      if (expected == null || !p.getRevision().equals(expected)) {
        problems.add(id.toString());
      } else if (p.getHoldStatus() != PayoutHoldStatus.NONE) {
        problems.add(id.toString());
      } else if (p.getStatus() != BaseRewardPayoutStatus.CONFIRMED) {
        problems.add(id.toString());
      }
    }
    if (!problems.isEmpty()) {
      throw new BulkPayoutConflictException("error.reward.payout.bulk_conflict", problems);
    }

    for (UUID id : ids) {
      rewardPayoutRepository
          .findById(id)
          .ifPresentOrElse(
              p -> {
                p.updateSchedule(request.scheduledAt());
                auditLogRepository.save(
                    ZoneEventAuditLog.builder()
                        .actorId(user.id())
                        .action("SCHEDULE_PAYOUT")
                        .targetType("REWARD_PAYOUT")
                        .targetId(id)
                        .detail(
                            Map.of(
                                "payoutType",
                                "TOP_LIKE",
                                "scheduledAt",
                                request.scheduledAt().toString()))
                        .build());
              },
              () ->
                  baseRewardPayoutRepository
                      .findById(id)
                      .ifPresent(
                          p -> {
                            p.updateSchedule(request.scheduledAt());
                            auditLogRepository.save(
                                ZoneEventAuditLog.builder()
                                    .actorId(user.id())
                                    .action("SCHEDULE_PAYOUT")
                                    .targetType("REWARD_PAYOUT")
                                    .targetId(id)
                                    .detail(
                                        Map.of(
                                            "payoutType",
                                            "BASE",
                                            "scheduledAt",
                                            request.scheduledAt().toString()))
                                    .build());
                          }));
    }

    AdminRewardPayoutBulkResultResDto result =
        new AdminRewardPayoutBulkResultResDto(ids.stream().map(UUID::toString).toList());
    idempotencyService.save(idempotencyKey, BULK_SCHEDULE_ENDPOINT, fingerprint, result);
    return result;
  }

  @Transactional
  public AdminRewardPayoutDetailResDto markMailSent(
      AuthenticatedUser user, AdminRewardPayoutMarkReqDto request, String idempotencyKey) {
    return applyTopLikeOnlyStep(
        user,
        request,
        idempotencyKey,
        MARK_MAIL_SENT_ENDPOINT,
        "MARK_MAIL_SENT",
        RewardPayoutStatus.CONFIRMED,
        (payout, at, note) -> payout.markMailSent(at == null ? OffsetDateTime.now() : at, note));
  }

  @Transactional
  public AdminRewardPayoutDetailResDto markInfoCollected(
      AuthenticatedUser user, AdminRewardPayoutMarkReqDto request, String idempotencyKey) {
    return applyTopLikeOnlyStep(
        user,
        request,
        idempotencyKey,
        MARK_INFO_COLLECTED_ENDPOINT,
        "MARK_INFO_COLLECTED",
        RewardPayoutStatus.MAIL_SENT,
        (payout, at, note) ->
            payout.markInfoCollected(at == null ? OffsetDateTime.now() : at, note));
  }

  private AdminRewardPayoutDetailResDto applyTopLikeOnlyStep(
      AuthenticatedUser user,
      AdminRewardPayoutMarkReqDto request,
      String idempotencyKey,
      String endpoint,
      String auditAction,
      RewardPayoutStatus requiredStatus,
      TopLikeStepAction action) {
    operatorAuthorization.requireOperator(user);
    String fingerprint =
        request.payoutId()
            + ":"
            + request.at()
            + ":"
            + request.note()
            + ":"
            + request.expectedRevision();
    Optional<String> replay = idempotencyService.findReplay(idempotencyKey, endpoint, fingerprint);
    if (replay.isPresent()) {
      return readJson(replay.get(), AdminRewardPayoutDetailResDto.class);
    }

    if (baseRewardPayoutRepository.findById(request.payoutId()).isPresent()) {
      throw new ConflictException("error.reward.payout.wrong_type");
    }
    RewardPayout payout =
        rewardPayoutRepository
            .findById(request.payoutId())
            .orElseThrow(() -> new ResourceNotFoundException("error.reward.payout.not_found"));
    if (!payout.getRevision().equals(request.expectedRevision())) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    if (payout.getHoldStatus() != PayoutHoldStatus.NONE) {
      throw new ConflictException("error.reward.payout.invalid_state");
    }
    if (payout.getStatus() != requiredStatus) {
      throw new ConflictException("error.reward.payout.invalid_state");
    }
    action.apply(payout, request.at(), request.note());
    try {
      rewardPayoutRepository.saveAndFlush(payout);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }

    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action(auditAction)
            .targetType("REWARD_PAYOUT")
            .targetId(payout.getId())
            .detail(Map.of("note", request.note() == null ? "" : request.note()))
            .build());
    AdminRewardPayoutDetailResDto result = AdminRewardPayoutDetailResDto.ofTopLike(payout);
    idempotencyService.save(idempotencyKey, endpoint, fingerprint, result);
    return result;
  }

  @Transactional
  public AdminRewardPayoutDetailResDto markSent(
      AuthenticatedUser user, AdminRewardPayoutMarkSentReqDto request, String idempotencyKey) {
    operatorAuthorization.requireOperator(user);
    String fingerprint =
        request.payoutId()
            + ":"
            + request.sentAt()
            + ":"
            + request.reference()
            + ":"
            + request.note()
            + ":"
            + request.expectedRevision();
    Optional<String> replay =
        idempotencyService.findReplay(idempotencyKey, MARK_SENT_ENDPOINT, fingerprint);
    if (replay.isPresent()) {
      return readJson(replay.get(), AdminRewardPayoutDetailResDto.class);
    }

    AdminRewardPayoutDetailResDto result;
    Optional<RewardPayout> topLike = rewardPayoutRepository.findById(request.payoutId());
    if (topLike.isPresent()) {
      result = markTopLikeSent(topLike.get(), request);
    } else {
      BaseRewardPayout base =
          baseRewardPayoutRepository
              .findById(request.payoutId())
              .orElseThrow(() -> new ResourceNotFoundException("error.reward.payout.not_found"));
      result = markBaseSent(base, request);
    }

    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action("MARK_SENT")
            .targetType("REWARD_PAYOUT")
            .targetId(request.payoutId())
            .detail(Map.of("payoutType", result.payoutType()))
            .build());
    idempotencyService.save(idempotencyKey, MARK_SENT_ENDPOINT, fingerprint, result);
    return result;
  }

  private AdminRewardPayoutDetailResDto markTopLikeSent(
      RewardPayout payout, AdminRewardPayoutMarkSentReqDto request) {
    if (!payout.getRevision().equals(request.expectedRevision())) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    if (payout.getHoldStatus() != PayoutHoldStatus.NONE) {
      throw new ConflictException("error.reward.payout.invalid_state");
    }
    if (payout.getStatus() != RewardPayoutStatus.INFO_COLLECTED) {
      throw new ConflictException("error.reward.payout.invalid_state");
    }
    payout.markSent(
        request.sentAt() == null ? OffsetDateTime.now() : request.sentAt(),
        request.reference(),
        request.note());
    try {
      rewardPayoutRepository.saveAndFlush(payout);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    return AdminRewardPayoutDetailResDto.ofTopLike(payout);
  }

  private AdminRewardPayoutDetailResDto markBaseSent(
      BaseRewardPayout payout, AdminRewardPayoutMarkSentReqDto request) {
    if (!payout.getRevision().equals(request.expectedRevision())) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    if (payout.getHoldStatus() != PayoutHoldStatus.NONE) {
      throw new ConflictException("error.reward.payout.invalid_state");
    }
    if (payout.getStatus() != BaseRewardPayoutStatus.CONFIRMED) {
      throw new ConflictException("error.reward.payout.invalid_state");
    }
    ZoneEventParticipation participation =
        participationRepository
            .findById(payout.getParticipationId())
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.participation.not_found"));
    UUID eventId = participation.getEvent().getId();
    var reward = payout.getReward();
    rewardService.grantBaseReward(
        participation.getUserId(),
        payout.getParticipationId(),
        eventId,
        reward == null ? null : reward.points(),
        reward == null ? null : reward.badgeCode());
    payout.markSent(
        request.sentAt() == null ? OffsetDateTime.now() : request.sentAt(), request.note());
    try {
      baseRewardPayoutRepository.saveAndFlush(payout);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ConflictException("error.reward.payout.stale_revision");
    }
    return AdminRewardPayoutDetailResDto.ofBase(payout, eventId);
  }

  @Transactional
  public AdminRewardPayoutDetailResDto retry(
      AuthenticatedUser user,
      UUID payoutId,
      AdminRewardPayoutRetryReqDto request,
      String idempotencyKey) {
    operatorAuthorization.requireOperator(user);
    String fingerprint = payoutId + ":" + request.note() + ":" + request.expectedRevision();
    Optional<String> replay =
        idempotencyService.findReplay(idempotencyKey, RETRY_ENDPOINT, fingerprint);
    if (replay.isPresent()) {
      return readJson(replay.get(), AdminRewardPayoutDetailResDto.class);
    }

    AdminRewardPayoutDetailResDto result;
    Optional<RewardPayout> topLike = rewardPayoutRepository.findById(payoutId);
    if (topLike.isPresent()) {
      RewardPayout p = topLike.get();
      if (!p.getRevision().equals(request.expectedRevision())) {
        throw new ConflictException("error.reward.payout.stale_revision");
      }
      if (p.getStatus() != RewardPayoutStatus.FAILED) {
        throw new ConflictException("error.reward.payout.invalid_state");
      }
      p.retry();
      try {
        rewardPayoutRepository.saveAndFlush(p);
      } catch (ObjectOptimisticLockingFailureException e) {
        throw new ConflictException("error.reward.payout.stale_revision");
      }
      result = AdminRewardPayoutDetailResDto.ofTopLike(p);
    } else {
      BaseRewardPayout p =
          baseRewardPayoutRepository
              .findById(payoutId)
              .orElseThrow(() -> new ResourceNotFoundException("error.reward.payout.not_found"));
      if (!p.getRevision().equals(request.expectedRevision())) {
        throw new ConflictException("error.reward.payout.stale_revision");
      }
      if (p.getStatus() != BaseRewardPayoutStatus.FAILED) {
        throw new ConflictException("error.reward.payout.invalid_state");
      }
      p.retry();
      UUID eventId =
          participationRepository
              .findById(p.getParticipationId())
              .map(ZoneEventParticipation::getEvent)
              .map(com.butingbe.domain.zoneevent.entity.ZoneEvent::getId)
              .orElse(null);
      try {
        baseRewardPayoutRepository.saveAndFlush(p);
      } catch (ObjectOptimisticLockingFailureException e) {
        throw new ConflictException("error.reward.payout.stale_revision");
      }
      result = AdminRewardPayoutDetailResDto.ofBase(p, eventId);
    }

    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action("RETRY_PAYOUT")
            .targetType("REWARD_PAYOUT")
            .targetId(payoutId)
            .detail(Map.of("payoutType", result.payoutType()))
            .build());
    idempotencyService.save(idempotencyKey, RETRY_ENDPOINT, fingerprint, result);
    return result;
  }

  @FunctionalInterface
  private interface TopLikeStepAction {
    void apply(RewardPayout payout, OffsetDateTime at, String note);
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
