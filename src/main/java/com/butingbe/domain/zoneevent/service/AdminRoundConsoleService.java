package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.reward.dto.response.SettlementReportResDto;
import com.butingbe.domain.reward.service.RewardSettlementService;
import com.butingbe.domain.zoneevent.dto.request.BackupTargetReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundCancelReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundCreateReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundPatchReqDto;
import com.butingbe.domain.zoneevent.dto.request.SwapTargetReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminRoundPageResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminRoundResDto;
import com.butingbe.domain.zoneevent.dto.response.SlotSuggestionResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventBackupTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import com.butingbe.domain.zoneevent.entity.ZoneEventSettlementReport;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetStatus;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventBackupTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventSettlementReportRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영 콘솔: 회차 캘린더·슬롯 배정·예비/우천 타겟·확정/취소·정산 재실행.
 *
 * <p>ACTIVE/CLOSED는 자동 전환({@link RoundTransitionService})으로만 도달하며, 이 서비스는 더 이상 수동 open/close를 제공하지
 * 않는다. 모든 메서드는 ROLE_ADMIN/MANAGER만 호출할 수 있고, 상태를 바꾸는 행위는 감사 로그에 남긴다.
 */
@Service
@RequiredArgsConstructor
public class AdminRoundConsoleService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;

  private final OperatorAuthorization operatorAuthorization;
  private final ZoneEventRoundRepository roundRepository;
  private final ZoneEventRoundSlotRepository slotRepository;
  private final ZoneEventBackupTargetRepository backupTargetRepository;
  private final ZoneEventRepository zoneEventRepository;
  private final ZoneEventAuthTargetRepository authTargetRepository;
  private final ZoneEventParticipationRepository participationRepository;
  private final ZoneEventSettlementReportRepository settlementReportRepository;
  private final ZoneEventAuditLogRepository auditLogRepository;
  private final RoundSlotSuggestionService suggestionService;
  private final RoundTransitionService transitionService;
  private final RewardSettlementService settlementService;

  @Transactional
  public AdminRoundResDto createRound(AuthenticatedUser user, RoundCreateReqDto request) {
    operatorAuthorization.requireOperator(user);
    if (roundRepository.existsByRoundNo(request.roundNo())) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    ZoneEventRound round =
        roundRepository.save(
            ZoneEventRound.builder()
                .roundType(request.roundType())
                .roundNo(request.roundNo())
                .name(request.name())
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .timezone(request.timezone())
                .excellenceReward(
                    request.excellenceReward() == null
                        ? null
                        : request.excellenceReward().toSnapshot())
                .build());
    audit(user, "CREATE_ROUND", "ROUND", round.getId(), Map.of("roundNo", round.getRoundNo()));
    return detailOf(round);
  }

  @Transactional
  public AdminRoundPageResDto listRounds(
      AuthenticatedUser user,
      String status,
      OffsetDateTime from,
      OffsetDateTime to,
      String keyword,
      Integer page,
      Integer size) {
    operatorAuthorization.requireOperator(user);
    RoundStatus statusFilter =
        status == null || status.isBlank() ? null : RoundStatus.valueOf(status.trim());
    int pageSize = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    int pageNumber = page == null || page < 0 ? 0 : page;

    Specification<ZoneEventRound> spec = buildRoundSpec(statusFilter, from, to, keyword);
    Page<ZoneEventRound> result =
        roundRepository.findAll(
            spec,
            PageRequest.of(
                pageNumber,
                pageSize,
                org.springframework.data.domain.Sort.by("startsAt").descending()));

    OffsetDateTime now = OffsetDateTime.now();
    for (ZoneEventRound round : result.getContent()) {
      transitionService.sync(round, now);
    }
    List<AdminRoundResDto> items =
        result.getContent().stream().map(round -> detailOf(round, false)).toList();
    return new AdminRoundPageResDto(
        items, pageNumber, pageSize, result.getTotalElements(), result.getTotalPages());
  }

  @Transactional
  public AdminRoundResDto roundDetail(AuthenticatedUser user, UUID roundId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventRound round = requireRound(roundId);
    transitionService.sync(round, OffsetDateTime.now());
    return detailOf(round);
  }

  @Transactional(readOnly = true)
  public SlotSuggestionResDto suggestSlots(AuthenticatedUser user, int authSlots) {
    operatorAuthorization.requireOperator(user);
    return suggestionService.suggest(
        OffsetDateTime.now(java.time.ZoneId.of("Asia/Seoul")), authSlots);
  }

  @Transactional
  public AdminRoundResDto patch(AuthenticatedUser user, UUID roundId, RoundPatchReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneEventRound round = requireRound(roundId);
    requireMatchingRevision(round.getRevision(), request.expectedRevision());
    round.applyEditable(
        request.name(),
        request.startsAt(),
        request.endsAt(),
        request.timezone(),
        request.roundType());
    audit(user, "PATCH_ROUND", "ROUND", roundId, null);
    return detailOf(round);
  }

  /** DRAFT → SCHEDULED. 정확히 4개 서로 다른 구역, 각 슬롯 ACTIVE 타겟 1개 이상이어야 한다. */
  @Transactional
  public AdminRoundResDto schedule(AuthenticatedUser user, UUID roundId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventRound round = requireRound(roundId);
    // 취소된 이벤트는 실제로 운영되지 않으므로 4구역 정족수에서 제외한다.
    List<ZoneEvent> events =
        zoneEventRepository.findByRoundId(roundId).stream()
            .filter(e -> e.getStatus() != ZoneEventStatus.CANCELLED)
            .toList();
    Set<String> distinctZones = new HashSet<>();
    for (ZoneEvent event : events) {
      distinctZones.add(event.getZoneId());
    }
    if (events.size() != 4 || distinctZones.size() != 4) {
      throw new IllegalArgumentException("error.zone_event.round_slots_incomplete");
    }
    for (ZoneEvent event : events) {
      boolean hasActiveTarget =
          authTargetRepository
              .findFirstByEvent_IdAndStatusOrderByCreatedAtAsc(
                  event.getId(), ZoneEventTargetStatus.ACTIVE)
              .isPresent();
      if (!hasActiveTarget) {
        throw new IllegalArgumentException("error.zone_event.round_target_missing");
      }
    }
    round.confirmSchedule();
    audit(user, "SCHEDULE_ROUND", "ROUND", roundId, null);
    return detailOf(round);
  }

  /** DRAFT/SCHEDULED/ACTIVE → CANCELLED. 연결된 구역 슬롯도 함께 취소하되 기존 이력은 보존한다. */
  @Transactional
  public AdminRoundResDto cancel(AuthenticatedUser user, UUID roundId, RoundCancelReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneEventRound round = requireRound(roundId);
    requireMatchingRevision(round.getRevision(), request.expectedRevision());
    round.cancel(request.reason());
    for (ZoneEvent event : zoneEventRepository.findByRoundId(roundId)) {
      if (event.getStatus() == ZoneEventStatus.SCHEDULED
          || event.getStatus() == ZoneEventStatus.ACTIVE) {
        event.markCancelled();
        for (ZoneEventParticipation open :
            participationRepository.findByEvent_IdAndStatusIn(
                event.getId(),
                List.of(
                    ParticipationStatus.JOINED,
                    ParticipationStatus.SUBMITTED,
                    ParticipationStatus.UNDER_REVIEW))) {
          open.cancel("ROUND_CANCELLED");
        }
      }
    }
    audit(user, "CANCEL_ROUND", "ROUND", roundId, Map.of("reason", request.reason()));
    return detailOf(round);
  }

  @Transactional
  public AdminRoundResDto addBackupTarget(
      AuthenticatedUser user, UUID roundId, BackupTargetReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneEventRound round = requireRound(roundId);
    ZoneEventBackupTarget target =
        backupTargetRepository.save(
            ZoneEventBackupTarget.builder()
                .round(round)
                .targetKind(request.targetKind())
                .landmarkId(request.landmarkId())
                .placeName(request.placeName())
                .guideText(request.guideText())
                .exampleFileKey(request.exampleFileKey())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .radiusM(request.radiusM())
                .build());
    audit(
        user, "ADD_BACKUP_TARGET", "ROUND", roundId, Map.of("targetId", target.getId().toString()));
    return detailOf(round);
  }

  @Transactional
  public AdminRoundResDto swapTarget(
      AuthenticatedUser user, UUID roundId, SwapTargetReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneEventRound round = requireRound(roundId);
    ZoneEventBackupTarget backup =
        backupTargetRepository
            .findById(request.backupTargetId())
            .filter(b -> b.getRound().getId().equals(roundId))
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
    ZoneEventAuthTarget target =
        authTargetRepository
            .findFirstByEvent_IdAndStatusOrderByCreatedAtAsc(
                request.eventId(), ZoneEventTargetStatus.ACTIVE)
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.target_not_found"));
    target.update(
        backup.getPlaceName(),
        backup.getGuideText(),
        backup.getExampleFileKey(),
        backup.getLatitude(),
        backup.getLongitude(),
        backup.getRadiusM());
    audit(
        user,
        "SWAP_TARGET",
        "EVENT",
        request.eventId(),
        Map.of("backupTargetId", request.backupTargetId().toString()));
    return detailOf(round);
  }

  @Transactional
  public Map<String, Object> settle(AuthenticatedUser user, UUID roundId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventRound round =
        roundRepository
            .findWithLockById(roundId)
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
    if (round.getStatus() == RoundStatus.SETTLED) {
      return settlementReport(user, roundId);
    }
    expireOpenParticipations(roundId);
    SettlementReportResDto prizeReport = settlementService.settleTopLike(roundId);
    OffsetDateTime now = OffsetDateTime.now();
    round.settle(now);
    Map<String, Object> report = assembleReport(roundId, now, prizeReport);
    settlementReportRepository.save(
        ZoneEventSettlementReport.builder().roundId(roundId).report(report).build());
    audit(user, "SETTLE_ROUND", "ROUND", roundId, null);
    return report;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> settlementReport(AuthenticatedUser user, UUID roundId) {
    operatorAuthorization.requireOperator(user);
    return settlementReportRepository
        .findById(roundId)
        .map(ZoneEventSettlementReport::getReport)
        .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
  }

  private void requireMatchingRevision(Long actual, Long expected) {
    if (!actual.equals(expected)) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
  }

  private void expireOpenParticipations(UUID roundId) {
    for (ZoneEvent event : zoneEventRepository.findByRoundId(roundId)) {
      for (ZoneEventParticipation p :
          participationRepository.findByEvent_IdAndStatusIn(
              event.getId(), List.of(ParticipationStatus.JOINED))) {
        p.cancel("EXPIRED");
      }
    }
  }

  private Map<String, Object> assembleReport(
      UUID roundId, OffsetDateTime settledAt, SettlementReportResDto prizeReport) {
    Map<String, List<Map<String, Object>>> prizesByEvent = new LinkedHashMap<>();
    for (SettlementReportResDto.EventPrizes ep : prizeReport.events()) {
      List<Map<String, Object>> prizes = new ArrayList<>();
      for (SettlementReportResDto.Prize prize : ep.prizes()) {
        prizes.add(
            Map.of(
                "userId",
                prize.userId(),
                "participationId",
                prize.participationId(),
                "rewardCode",
                prize.rewardCode(),
                "status",
                prize.status()));
      }
      prizesByEvent.put(ep.eventId(), prizes);
    }

    List<Map<String, Object>> events = new ArrayList<>();
    for (ZoneEvent event : zoneEventRepository.findByRoundId(roundId)) {
      long participants = participationRepository.countByEvent_Id(event.getId());
      long success =
          participationRepository.countByEvent_IdAndStatus(
              event.getId(), ParticipationStatus.SUCCESS);
      List<ZoneEventParticipation> top =
          participationRepository.findTopPublicSuccessByEvent(event.getId(), PageRequest.of(0, 1));
      Map<String, Object> eventReport = new LinkedHashMap<>();
      eventReport.put("eventId", event.getId().toString());
      eventReport.put("zoneId", event.getZoneId());
      eventReport.put("participants", participants);
      eventReport.put("success", success);
      eventReport.put("successRate", participants == 0 ? 0.0 : (double) success / participants);
      eventReport.put(
          "topContentParticipationId", top.isEmpty() ? null : top.get(0).getId().toString());
      eventReport.put("prizes", prizesByEvent.getOrDefault(event.getId().toString(), List.of()));
      events.add(eventReport);
    }

    Map<String, Object> report = new LinkedHashMap<>();
    report.put("roundId", roundId.toString());
    report.put("settledAt", settledAt.toString());
    report.put("events", events);
    return report;
  }

  private AdminRoundResDto detailOf(ZoneEventRound round) {
    return detailOf(round, true);
  }

  /**
   * 회차 상세 DTO를 만든다.
   *
   * <p>{@code includeCounts=false}면 슬롯별 참여/성공/검수 카운트 질의(슬롯당 3회)를 생략한다. 목록 엔드포인트는 페이지당 회차 수 × 슬롯 수만큼
   * 질의가 늘어나므로(N+1) 카운트를 0으로 두고, 실제 카운트는 상세 조회에서만 제공한다.
   */
  private AdminRoundResDto detailOf(ZoneEventRound round, boolean includeCounts) {
    List<ZoneEventRoundSlot> slots = slotRepository.findByRound_Id(round.getId());
    Map<String, long[]> countsByEventId = new HashMap<>();
    if (includeCounts) {
      for (ZoneEventRoundSlot slot : slots) {
        if (slot.getEventId() == null) {
          continue;
        }
        String eventId = slot.getEventId().toString();
        long participants = participationRepository.countByEvent_Id(slot.getEventId());
        long success =
            participationRepository.countByEvent_IdAndStatus(
                slot.getEventId(), ParticipationStatus.SUCCESS);
        long underReview =
            participationRepository.countByEvent_IdAndStatus(
                slot.getEventId(), ParticipationStatus.UNDER_REVIEW);
        countsByEventId.put(eventId, new long[] {participants, success, underReview});
      }
    }
    return AdminRoundResDto.of(
        round, slots, backupTargetRepository.findByRound_Id(round.getId()), countsByEventId);
  }

  private Specification<ZoneEventRound> buildRoundSpec(
      RoundStatus status, OffsetDateTime from, OffsetDateTime to, String keyword) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("startsAt"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("startsAt"), to));
      }
      if (keyword != null && !keyword.isBlank()) {
        predicates.add(cb.like(root.get("name"), "%" + keyword + "%"));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private ZoneEventRound requireRound(UUID roundId) {
    return roundRepository
        .findById(roundId)
        .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
  }

  private void audit(
      AuthenticatedUser user,
      String action,
      String targetType,
      UUID targetId,
      Map<String, Object> detail) {
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action(action)
            .targetType(targetType)
            .targetId(targetId)
            .detail(detail)
            .build());
  }
}
