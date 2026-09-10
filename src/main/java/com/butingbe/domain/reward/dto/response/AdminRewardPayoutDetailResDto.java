package com.butingbe.domain.reward.dto.response;

import com.butingbe.domain.reward.entity.BaseRewardPayout;
import com.butingbe.domain.reward.entity.RewardPayout;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * payoutType은 TOP_LIKE 또는 BASE. 상대 타입에 없는 필드는 null(예: BASE는 rankN·likeCountAtClose·mailedAt 등이
 * null).
 */
public record AdminRewardPayoutDetailResDto(
    String payoutId,
    String payoutType,
    String eventId,
    String participationId,
    Integer rankN,
    Long likeCountAtClose,
    RewardSnapshot reward,
    String status,
    String holdStatus,
    OffsetDateTime scheduledAt,
    String confirmedBy,
    OffsetDateTime confirmedAt,
    OffsetDateTime mailedAt,
    OffsetDateTime informationCollectedAt,
    OffsetDateTime sentAt,
    OffsetDateTime paidAt,
    String reference,
    String memo,
    String failureCode,
    Long revision,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static AdminRewardPayoutDetailResDto ofTopLike(RewardPayout p) {
    return new AdminRewardPayoutDetailResDto(
        p.getId().toString(),
        "TOP_LIKE",
        p.getEventId().toString(),
        p.getParticipationId().toString(),
        p.getRankN(),
        p.getLikeCountAtClose(),
        p.getReward(),
        p.getStatus().name(),
        p.getHoldStatus().name(),
        p.getScheduledAt(),
        p.getConfirmedBy() == null ? null : p.getConfirmedBy().toString(),
        p.getConfirmedAt(),
        p.getMailedAt(),
        p.getInformationCollectedAt(),
        p.getSentAt(),
        null,
        p.getReference(),
        p.getMemo(),
        p.getFailureCode(),
        p.getRevision(),
        p.getCreatedAt(),
        p.getUpdatedAt());
  }

  public static AdminRewardPayoutDetailResDto ofBase(BaseRewardPayout p, UUID eventId) {
    return new AdminRewardPayoutDetailResDto(
        p.getId().toString(),
        "BASE",
        eventId == null ? null : eventId.toString(),
        p.getParticipationId().toString(),
        null,
        null,
        p.getReward(),
        p.getStatus().name(),
        p.getHoldStatus().name(),
        p.getScheduledAt(),
        p.getConfirmedBy() == null ? null : p.getConfirmedBy().toString(),
        p.getConfirmedAt(),
        null,
        null,
        null,
        p.getPaidAt(),
        null,
        p.getMemo(),
        p.getFailureCode(),
        p.getRevision(),
        p.getCreatedAt(),
        p.getUpdatedAt());
  }
}
