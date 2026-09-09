package com.butingbe.domain.reward.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.reward.dto.response.PayoutGenerateResDto;
import com.butingbe.domain.reward.entity.RewardPayout;
import com.butingbe.domain.reward.repository.RewardPayoutRepository;
import com.butingbe.domain.zoneevent.entity.ReportStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import com.butingbe.domain.zoneevent.entity.ZoneEventRankingSnapshot;
import com.butingbe.domain.zoneevent.repository.ZoneEventAuditLogRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRankingSnapshotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확정된 수상자({@link ZoneEventRankingSnapshot#getFinalized()})만 대상으로 TOP_LIKE 지급 후보({@link
 * RewardPayout})를 생성한다. 참여 UK로 멱등이라 재실행해도 이미 만든 건은 건드리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class RewardPayoutService {

  private static final List<ReportStatus> UNRESOLVED =
      List.of(ReportStatus.OPEN, ReportStatus.REVIEWING);

  private final OperatorAuthorization operatorAuthorization;
  private final ZoneEventRepository zoneEventRepository;
  private final ZoneEventRankingSnapshotRepository snapshotRepository;
  private final ZoneEventReportRepository reportRepository;
  private final RewardPayoutRepository payoutRepository;
  private final ZoneEventAuditLogRepository auditLogRepository;

  @Transactional
  public PayoutGenerateResDto generate(AuthenticatedUser user, UUID eventId) {
    operatorAuthorization.requireOperator(user);
    ZoneEvent event =
        zoneEventRepository
            .findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));

    int created = 0;
    int alreadyExists = 0;
    int heldOnCreate = 0;
    for (ZoneEventRankingSnapshot row : snapshotRepository.findByEventIdAndFinalizedTrue(eventId)) {
      if (payoutRepository.existsByParticipationId(row.getParticipationId())) {
        alreadyExists++;
        continue;
      }
      RewardPayout payout =
          RewardPayout.builder()
              .eventId(eventId)
              .participationId(row.getParticipationId())
              .rankN(row.getRankN())
              .likeCountAtClose(row.getLikeCountAtClose())
              .reward(event.getExcellenceReward())
              .build();
      if (reportRepository.existsByParticipationIdAndStatusIn(
          row.getParticipationId(), UNRESOLVED)) {
        payout.hold();
        heldOnCreate++;
      }
      payoutRepository.save(payout);
      created++;
    }

    auditLogRepository.save(
        ZoneEventAuditLog.builder()
            .actorId(user.id())
            .action("GENERATE_PAYOUTS")
            .targetType("EVENT")
            .targetId(eventId)
            .detail(
                Map.of(
                    "created",
                    created,
                    "alreadyExists",
                    alreadyExists,
                    "heldOnCreate",
                    heldOnCreate))
            .build());
    return new PayoutGenerateResDto(eventId.toString(), created, alreadyExists, heldOnCreate);
  }
}
