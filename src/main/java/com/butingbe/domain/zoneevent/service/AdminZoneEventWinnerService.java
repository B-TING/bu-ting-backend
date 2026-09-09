package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.zoneevent.dto.response.AdminTopNResDto;
import com.butingbe.domain.zoneevent.dto.response.TopNCandidateResDto;
import com.butingbe.domain.zoneevent.dto.response.TopNZoneGroupResDto;
import com.butingbe.domain.zoneevent.entity.ReportStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventRankingSnapshot;
import com.butingbe.domain.zoneevent.repository.ZoneEventRankingSnapshotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Top N 경계 동점 후보 조회, 관리자 최종 수상자 확정. */
@Service
@RequiredArgsConstructor
public class AdminZoneEventWinnerService {

  private static final List<ReportStatus> UNRESOLVED =
      List.of(ReportStatus.OPEN, ReportStatus.REVIEWING);

  private final OperatorAuthorization operatorAuthorization;
  private final ZoneEventRepository zoneEventRepository;
  private final ZoneEventRankingSnapshotRepository snapshotRepository;
  private final ZoneEventReportRepository reportRepository;

  /** roundId의 각 구역(이벤트)별 Top N 경계 후보 전체를 돌려준다. eventId를 주면 그 이벤트만. */
  @Transactional(readOnly = true)
  public AdminTopNResDto topN(AuthenticatedUser user, UUID roundId, UUID eventId) {
    operatorAuthorization.requireOperator(user);
    List<ZoneEvent> events = zoneEventRepository.findByRoundId(roundId);
    List<TopNZoneGroupResDto> zones =
        events.stream()
            .filter(e -> eventId == null || e.getId().equals(eventId))
            .map(this::zoneGroupOf)
            .toList();
    return new AdminTopNResDto(roundId.toString(), zones);
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
                        row,
                        reportRepository.existsByParticipationIdAndStatusIn(
                            row.getParticipationId(), UNRESOLVED)))
            .toList();
    return new TopNZoneGroupResDto(
        event.getId().toString(), event.getZoneId(), version, candidates);
  }
}
