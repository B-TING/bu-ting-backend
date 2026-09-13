package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.zoneevent.dto.request.WinnerConfirmReqDto;
import com.butingbe.domain.zoneevent.dto.response.AdminTopNResDto;
import com.butingbe.domain.zoneevent.dto.response.TopNCandidateResDto;
import com.butingbe.domain.zoneevent.dto.response.TopNZoneGroupResDto;
import com.butingbe.domain.zoneevent.dto.response.WinnerConfirmResDto;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import com.butingbe.domain.zoneevent.entity.ZoneEventRankingSnapshot;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRankingSnapshotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Top N 경계 동점 후보 조회, 관리자 최종 수상자 확정. */
@Service
@RequiredArgsConstructor
public class AdminZoneEventWinnerService {

  private static final String CONFIRM_ENDPOINT = "zone-event-winner-confirm";

  private final OperatorAuthorization operatorAuthorization;
  private final ZoneEventRoundRepository roundRepository;
  private final ZoneEventRepository zoneEventRepository;
  private final ZoneEventRankingSnapshotRepository snapshotRepository;
  private final ZoneEventReportRepository reportRepository;
  private final ZoneEventAuditLogRepository auditLogRepository;
  private final IdempotencyService idempotencyService;
  private final ObjectMapper objectMapper;

  /** roundId의 각 구역(이벤트)별 Top N 경계 후보 전체를 돌려준다. eventId를 주면 그 이벤트만. */
  @Transactional(readOnly = true)
  public AdminTopNResDto topN(AuthenticatedUser user, UUID roundId, UUID eventId) {
    operatorAuthorization.requireOperator(user);
    requireRound(roundId);
    List<ZoneEvent> events = zoneEventRepository.findByRoundId(roundId);
    List<TopNZoneGroupResDto> zones =
        events.stream()
            .filter(e -> eventId == null || e.getId().equals(eventId))
            .map(this::zoneGroupOf)
            .toList();
    return new AdminTopNResDto(roundId.toString(), zones);
  }

  private void requireRound(UUID roundId) {
    if (!roundRepository.existsById(roundId)) {
      throw new ResourceNotFoundException("error.zone_event.not_found");
    }
  }

  /** 경계 동점 후보 중 관리자가 선택한 participationId만 최종 수상자로 확정한다. 이미 확정된 id는 그대로 둔다(추가 확정만 가능). */
  @Transactional
  public WinnerConfirmResDto confirmWinners(
      AuthenticatedUser user, UUID eventId, WinnerConfirmReqDto request, String idempotencyKey) {
    operatorAuthorization.requireOperator(user);
    List<UUID> sortedIds = request.participationIds().stream().sorted().toList();
    String fingerprint =
        eventId
            + ":"
            + request.snapshotId()
            + ":"
            + sortedIds
            + ":"
            + request.selectionReason()
            + ":"
            + request.expectedRevision();
    Optional<String> replay =
        idempotencyService.findReplay(idempotencyKey, CONFIRM_ENDPOINT, fingerprint);
    if (replay.isPresent()) {
      return readJson(replay.get(), WinnerConfirmResDto.class);
    }

    ZoneEventRankingSnapshot anchor =
        snapshotRepository
            .findById(request.snapshotId())
            .filter(s -> s.getEventId().equals(eventId))
            .orElseThrow(
                () -> new ResourceNotFoundException("error.zone_event.ranking_snapshot.not_found"));
    if (!anchor.getVersion().equals(request.expectedRevision())) {
      throw new ConflictException("error.zone_event.winner.stale_revision");
    }

    List<ZoneEventRankingSnapshot> targets = new ArrayList<>();
    for (UUID participationId : request.participationIds()) {
      ZoneEventRankingSnapshot row =
          snapshotRepository
              .findByEventIdAndVersionAndParticipationId(
                  eventId, anchor.getVersion(), participationId)
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException("error.zone_event.ranking_snapshot.not_found"));
      if (reportRepository.hasUnresolvedReports(participationId)) {
        throw new ConflictException("error.zone_event.winner.held_by_report");
      }
      targets.add(row);
    }
    requireWithinTopN(eventId, anchor.getVersion(), targets);

    List<String> confirmed = new ArrayList<>();
    for (ZoneEventRankingSnapshot row : targets) {
      row.markFinalized();
      confirmed.add(row.getParticipationId().toString());
    }

    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("version", anchor.getVersion());
    detail.put("participationIds", confirmed);
    detail.put("selectionReason", request.selectionReason());
    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action("CONFIRM_WINNERS")
            .targetType("EVENT")
            .targetId(eventId)
            .detail(detail)
            .build());

    WinnerConfirmResDto result =
        new WinnerConfirmResDto(eventId.toString(), anchor.getVersion(), confirmed);
    idempotencyService.save(idempotencyKey, CONFIRM_ENDPOINT, fingerprint, result);
    return result;
  }

  /**
   * 이미 확정된 수상자 수 + 이번에 새로 확정될 수 = 정원(topN)을 넘지 못하게 막는다.
   *
   * <p>{@code markFinalized()}에는 역연산이 없으므로, 초과 확정은 되돌릴 수 없고 {@code payouts/generate}가 그대로 지급 후보를
   * 만든다. 이미 확정된 id를 다시 보내는 것은 멱등 no-op이라 정원에 새로 계산하지 않는다. 우수 보상이 없는 이벤트는 정원 0으로 본다(그런 이벤트는 스냅샷 자체가
   * 생기지 않아 실제로는 도달하지 않는 방어 코드다).
   */
  private void requireWithinTopN(
      UUID eventId, Integer version, List<ZoneEventRankingSnapshot> targets) {
    RewardSnapshot excellence =
        zoneEventRepository.findById(eventId).map(ZoneEvent::getExcellenceReward).orElse(null);
    int topN = excellence == null || excellence.topN() == null ? 0 : excellence.topN();
    long alreadyFinalized =
        snapshotRepository.countByEventIdAndVersionAndFinalizedTrue(eventId, version);
    long newlyFinalizing = targets.stream().filter(row -> !row.getFinalized()).count();
    if (alreadyFinalized + newlyFinalizing > topN) {
      throw new ConflictException("error.zone_event.winner.topn_exceeded");
    }
  }

  private <T> T readJson(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JacksonException e) {
      throw new IllegalStateException("Failed to deserialize idempotent response.", e);
    }
  }

  private TopNZoneGroupResDto zoneGroupOf(ZoneEvent event) {
    List<ZoneEventRankingSnapshot> rows =
        snapshotRepository.findByEventIdAndVersionOrderByRankNAsc(event.getId(), 1);
    Integer version = rows.isEmpty() ? null : rows.get(0).getVersion();
    List<TopNCandidateResDto> candidates =
        rows.stream()
            .map(
                row ->
                    TopNCandidateResDto.of(
                        row, reportRepository.hasUnresolvedReports(row.getParticipationId())))
            .toList();
    return new TopNZoneGroupResDto(
        event.getId().toString(), event.getZoneId(), version, candidates);
  }
}
