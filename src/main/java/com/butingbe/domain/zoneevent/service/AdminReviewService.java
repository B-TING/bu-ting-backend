package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.domain.reward.service.RewardRevokeService;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ReportStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventReportRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영자 검수: 성공 참여의 회수·신고 자동 숨김 해제. 검수 큐/승인/반려는 {@link AdminZoneEventReviewService}로 이동했다. */
@Service
@RequiredArgsConstructor
public class AdminReviewService {

  private final ZoneEventParticipationRepository participationRepository;
  private final ZoneEventReportRepository reportRepository;
  private final RewardRevokeService rewardRevokeService;
  private final OperatorAuthorization operatorAuthorization;

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
}
