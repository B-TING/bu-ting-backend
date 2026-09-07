package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.reward.dto.response.SettlementReportResDto;
import com.butingbe.domain.reward.service.RewardSettlementService;
import com.butingbe.domain.zoneevent.dto.request.BackupTargetReqDto;
import com.butingbe.domain.zoneevent.dto.request.RoundCreateReqDto;
import com.butingbe.domain.zoneevent.dto.request.SlotReassignReqDto;
import com.butingbe.domain.zoneevent.dto.request.SwapTargetReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminRoundResDto;
import com.butingbe.domain.zoneevent.dto.response.SlotSuggestionResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.SlotKind;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventBackupTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import com.butingbe.domain.zoneevent.entity.ZoneEventSettlementReport;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuthTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventBackupTargetRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventSettlementReportRepository;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영 콘솔: 회차 캘린더·슬롯 배정·예비/우천 타겟·수동 전환·정산 재실행(FR-OPS-04/06/07).
 *
 * <p>모든 메서드는 ROLE_ADMIN/MANAGER만 호출할 수 있고, 상태를 바꾸는 행위는 감사 로그(NFR-12)에 남긴다. open/close는 스케줄러와 같은
 * 규칙으로 슬롯에 연결된 이벤트를 함께 전환하고, settle은 회차 행을 잠근 뒤 TOP_LIKE 정산을 오케스트레이션하며 재실행해도 같은 결과가 된다(BR-12).
 */
@Service
@RequiredArgsConstructor
public class AdminRoundConsoleService {

  private static final String SEOUL = "Asia/Seoul";

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
  private final RewardSettlementService settlementService;

  @Transactional
  public AdminRoundResDto createRound(AuthenticatedUser user, RoundCreateReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneEventRound round =
        roundRepository.save(
            ZoneEventRound.builder()
                .roundType(request.roundType())
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .timezone(request.timezone())
                .status(RoundStatus.SCHEDULED)
                .build());
    for (String zone : request.zoneIds()) {
      slotRepository.save(
          ZoneEventRoundSlot.builder()
              .round(round)
              .slotKind(SlotKind.AUTH)
              .zoneId(parseZone(zone))
              .build());
    }
    audit(user, "CREATE_ROUND", "ROUND", round.getId(), Map.of("zones", request.zoneIds()));
    return detailOf(round);
  }

  @Transactional(readOnly = true)
  public List<AdminRoundResDto> listRounds(
      AuthenticatedUser user, OffsetDateTime from, OffsetDateTime to) {
    operatorAuthorization.requireOperator(user);
    return roundRepository.findByStartsAtBetweenOrderByStartsAtAsc(from, to).stream()
        .map(this::detailOf)
        .toList();
  }

  @Transactional(readOnly = true)
  public AdminRoundResDto roundDetail(AuthenticatedUser user, UUID roundId) {
    operatorAuthorization.requireOperator(user);
    return detailOf(requireRound(roundId));
  }

  @Transactional(readOnly = true)
  public SlotSuggestionResDto suggestSlots(AuthenticatedUser user, int authSlots) {
    operatorAuthorization.requireOperator(user);
    return suggestionService.suggest(OffsetDateTime.now(ZoneId.of(SEOUL)), authSlots);
  }

  @Transactional
  public AdminRoundResDto reassignSlot(
      AuthenticatedUser user, UUID roundId, SlotReassignReqDto request) {
    operatorAuthorization.requireOperator(user);
    ZoneEventRound round = requireRound(roundId);
    ZoneEventRoundSlot slot =
        slotRepository
            .findById(request.slotId())
            .filter(s -> s.getRound().getId().equals(roundId))
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
    slot.reassignZone(parseZone(request.zoneId()));
    audit(user, "REASSIGN_SLOT", "SLOT", slot.getId(), Map.of("zone", request.zoneId()));
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

  /** 예비 타겟으로 이벤트의 인증 타겟을 즉시 교체(우천 대응, FR-RND-05). */
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
            .findByEvent_Id(request.eventId())
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

  /** SCHEDULED → OPEN. 슬롯에 연결된 이벤트도 활성화한다. 이미 OPEN 이후면 아무것도 하지 않는다(멱등). */
  @Transactional
  public AdminRoundResDto open(AuthenticatedUser user, UUID roundId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventRound round = requireRound(roundId);
    if (round.getStatus() == RoundStatus.SCHEDULED) {
      round.open();
      transitionSlotEvents(round, ZoneEventStatus.SCHEDULED, ZoneEventStatus.ACTIVE);
      audit(user, "OPEN_ROUND", "ROUND", roundId, null);
    }
    return detailOf(round);
  }

  /** OPEN → CLOSED. 슬롯에 연결된 이벤트도 종료한다(멱등). */
  @Transactional
  public AdminRoundResDto close(AuthenticatedUser user, UUID roundId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventRound round = requireRound(roundId);
    if (round.getStatus() == RoundStatus.OPEN) {
      round.close();
      transitionSlotEvents(round, ZoneEventStatus.ACTIVE, ZoneEventStatus.CLOSED);
      audit(user, "CLOSE_ROUND", "ROUND", roundId, null);
    }
    return detailOf(round);
  }

  /**
   * 회차 정산 재실행(FR-OPS-07). 회차 행을 잠그고, 미완료 참여를 만료 처리한 뒤 TOP_LIKE 정산을 실행하고 정산 리포트를 저장한다. 이미 SETTLED면
   * 저장된 리포트를 그대로 돌려준다(BR-12).
   */
  @Transactional
  public Map<String, Object> settle(AuthenticatedUser user, UUID roundId) {
    operatorAuthorization.requireOperator(user);
    ZoneEventRound round =
        roundRepository
            .findWithLockById(roundId)
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
    if (round.getStatus() == RoundStatus.SETTLED) {
      return settlementReport(user, roundId); // 저장된 리포트를 그대로(BR-12)
    }
    expireOpenParticipations(roundId);
    SettlementReportResDto prizeReport = settlementService.settleTopLike(roundId);
    OffsetDateTime now = OffsetDateTime.now();
    round.settle(now);
    Map<String, Object> report = assembleReport(roundId, now, prizeReport);
    // 회차 락 + SETTLED 조기 반환이 리포트가 회차당 한 번만 저장되도록 보장한다.
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
                "userId", prize.userId(),
                "participationId", prize.participationId(),
                "rewardCode", prize.rewardCode(),
                "status", prize.status()));
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

  private void transitionSlotEvents(
      ZoneEventRound round, ZoneEventStatus from, ZoneEventStatus to) {
    List<UUID> eventIds =
        slotRepository.findByRound_Id(round.getId()).stream()
            .map(ZoneEventRoundSlot::getEventId)
            .filter(id -> id != null)
            .toList();
    if (eventIds.isEmpty()) {
      return;
    }
    for (ZoneEvent event : zoneEventRepository.findAllById(eventIds)) {
      if (event.getStatus() != from) {
        continue;
      }
      if (to == ZoneEventStatus.ACTIVE) {
        event.activate();
      } else {
        event.close();
      }
    }
  }

  private AdminRoundResDto detailOf(ZoneEventRound round) {
    return AdminRoundResDto.of(
        round,
        slotRepository.findByRound_Id(round.getId()),
        backupTargetRepository.findByRound_Id(round.getId()));
  }

  private ZoneEventRound requireRound(UUID roundId) {
    return roundRepository
        .findById(roundId)
        .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
  }

  private String parseZone(String zone) {
    try {
      return ChatZone.fromString(zone).name();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("error.zone_event.invalid_zone");
    }
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
