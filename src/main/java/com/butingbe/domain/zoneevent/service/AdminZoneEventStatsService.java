package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.reward.entity.BaseRewardPayoutStatus;
import com.butingbe.domain.reward.entity.RewardPayoutStatus;
import com.butingbe.domain.reward.repository.BaseRewardPayoutRepository;
import com.butingbe.domain.reward.repository.RewardPayoutRepository;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventStatsItemResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventStatsResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventSubmissionRepository;
import com.butingbe.domain.zonetitle.repository.UserZoneTitleRepository;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회차·슬롯별 운영 통계(참여·검수·신고·지급·칭호). ROLE_ADMIN/MANAGER 전용. */
@Service
@RequiredArgsConstructor
public class AdminZoneEventStatsService {

  private final ZoneEventRoundRepository roundRepository;
  private final ZoneEventRoundSlotRepository slotRepository;
  private final ZoneEventParticipationRepository participationRepository;
  private final ZoneEventSubmissionRepository submissionRepository;
  private final ZoneEventReportRepository reportRepository;
  private final BaseRewardPayoutRepository baseRewardPayoutRepository;
  private final RewardPayoutRepository rewardPayoutRepository;
  private final UserZoneTitleRepository userZoneTitleRepository;
  private final OperatorAuthorization operatorAuthorization;

  @Transactional(readOnly = true)
  public AdminZoneEventStatsResDto stats(
      AuthenticatedUser user, UUID roundId, OffsetDateTime from, OffsetDateTime to) {
    operatorAuthorization.requireOperator(user);
    if (roundId == null && (from == null || to == null)) {
      throw new IllegalArgumentException("error.zone_event.stats.round_or_range_required");
    }

    List<ZoneEventRound> rounds =
        roundId != null
            ? List.of(
                roundRepository
                    .findById(roundId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found")))
            : roundRepository.findByStartsAtBetweenOrderByStartsAtAsc(from, to);

    List<AdminZoneEventStatsItemResDto> items = new ArrayList<>();
    for (ZoneEventRound round : rounds) {
      for (ZoneEventRoundSlot slot : slotRepository.findByRound_Id(round.getId())) {
        items.add(buildSlotStats(round, slot));
      }
    }
    return new AdminZoneEventStatsResDto(items);
  }

  private AdminZoneEventStatsItemResDto buildSlotStats(
      ZoneEventRound round, ZoneEventRoundSlot slot) {
    UUID eventId = slot.getEventId();
    if (eventId == null) {
      return AdminZoneEventStatsItemResDto.empty(round.getId(), slot.getId(), slot.getZoneId());
    }

    long joined = participationRepository.countByEvent_Id(eventId);
    long submitted =
        participationRepository.countByEvent_IdAndCurrentSubmissionIdIsNotNull(eventId);
    long attempts = submissionRepository.countByParticipation_Event_Id(eventId);
    long success =
        participationRepository.countByEvent_IdAndStatus(eventId, ParticipationStatus.SUCCESS);
    long fail = participationRepository.countByEvent_IdAndStatus(eventId, ParticipationStatus.FAIL);
    long pendingReview =
        participationRepository.countByEvent_IdAndStatus(eventId, ParticipationStatus.UNDER_REVIEW);
    double successRate = submitted == 0 ? 0.0 : (double) success / submitted;
    long openReports = reportRepository.countUnresolvedByEventId(eventId);
    long basePaid =
        baseRewardPayoutRepository.countByEventIdAndStatus(eventId, BaseRewardPayoutStatus.PAID);
    long specialSent =
        rewardPayoutRepository.countByEventIdAndStatus(eventId, RewardPayoutStatus.SENT);

    List<ZoneEventParticipation> top =
        participationRepository.findTopPublicSuccessByEvent(eventId, PageRequest.of(0, 1));
    AdminZoneEventStatsItemResDto.TopContent topContent =
        top.isEmpty()
            ? null
            : new AdminZoneEventStatsItemResDto.TopContent(
                top.get(0).getId().toString(), top.get(0).getLikeCount());

    long newTitleGrants =
        userZoneTitleRepository.countByZoneIdAndEarnedAtBetween(
            slot.getZoneId(), round.getStartsAt(), round.getEndsAt());

    return new AdminZoneEventStatsItemResDto(
        round.getId().toString(),
        slot.getId().toString(),
        slot.getZoneId(),
        eventId.toString(),
        joined,
        submitted,
        attempts,
        success,
        fail,
        pendingReview,
        successRate,
        openReports,
        basePaid,
        specialSent,
        topContent,
        newTitleGrants);
  }
}
