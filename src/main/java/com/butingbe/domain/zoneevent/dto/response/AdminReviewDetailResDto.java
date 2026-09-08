package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import java.time.OffsetDateTime;
import java.util.List;

/** 검수 상세: 사용자 표시 정보 + 현재 제출 + 전체 제출 이력 + 당시(제출 시점) 타겟/보상 스냅샷. */
public record AdminReviewDetailResDto(
    String participationId,
    String eventId,
    String roundId,
    String zoneId,
    String userId,
    String userNickname,
    String userEmail,
    String status,
    RewardSnapshot rewardSnapshot,
    AdminSubmissionDetailResDto currentSubmission,
    List<AdminSubmissionDetailResDto> submissionHistory,
    OffsetDateTime joinedAt) {

  public static AdminReviewDetailResDto of(
      ZoneEventParticipation p,
      String userNickname,
      String userEmail,
      AdminSubmissionDetailResDto currentSubmission,
      List<AdminSubmissionDetailResDto> submissionHistory) {
    return new AdminReviewDetailResDto(
        p.getId().toString(),
        p.getEvent().getId().toString(),
        p.getEvent().getRoundId() == null ? null : p.getEvent().getRoundId().toString(),
        p.getEvent().getZoneId(),
        p.getUserId().toString(),
        userNickname,
        userEmail,
        p.getStatus().name(),
        p.getEvent().getBaseReward(),
        currentSubmission,
        submissionHistory,
        p.getJoinedAt());
  }
}
