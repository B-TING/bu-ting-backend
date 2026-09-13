package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.reward.dto.response.GrantedRewardDto;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import java.time.OffsetDateTime;
import java.util.List;

/** 내 참여 이력 한 항목. 미디어는 요청 시점 presigned URL과 만료 시간(초). 반려된 참여는 사유·재제출 가능 여부·제출 이력을 함께 담는다. */
public record ParticipationHistoryItemResDto(
    String participationId,
    String status,
    EventBriefResDto event,
    String mediaUrl,
    Integer mediaUrlExpiresIn,
    String content,
    long likeCount,
    int commentCount,
    String visibility,
    List<GrantedRewardDto> rewards,
    OffsetDateTime joinedAt,
    OffsetDateTime completedAt,
    String rejectionReason,
    boolean canResubmit,
    List<SubmissionHistoryItemResDto> submissions) {

  public static ParticipationHistoryItemResDto of(
      ZoneEventParticipation participation,
      String mediaUrl,
      Integer mediaUrlExpiresIn,
      List<GrantedRewardDto> rewards,
      String rejectionReason,
      boolean canResubmit,
      List<SubmissionHistoryItemResDto> submissions) {
    return new ParticipationHistoryItemResDto(
        participation.getId().toString(),
        participation.getStatus().name(),
        EventBriefResDto.from(participation.getEvent()),
        mediaUrl,
        mediaUrl == null ? null : mediaUrlExpiresIn,
        participation.getContent(),
        participation.getLikeCount(),
        participation.getCommentCount(),
        participation.getVisibility().name(),
        rewards,
        participation.getJoinedAt(),
        participation.getCompletedAt(),
        rejectionReason,
        canResubmit,
        submissions);
  }
}
