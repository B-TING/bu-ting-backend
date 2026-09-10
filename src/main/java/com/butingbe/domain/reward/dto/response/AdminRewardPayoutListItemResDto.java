package com.butingbe.domain.reward.dto.response;

import com.butingbe.domain.reward.entity.BaseRewardPayout;
import com.butingbe.domain.reward.entity.RewardPayout;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminRewardPayoutListItemResDto(
    String payoutId,
    String payoutType,
    String participationId,
    String eventId,
    String status,
    String holdStatus,
    OffsetDateTime scheduledAt,
    RewardSnapshot reward,
    Long revision) {

  public static AdminRewardPayoutListItemResDto ofTopLike(RewardPayout p) {
    return new AdminRewardPayoutListItemResDto(
        p.getId().toString(),
        "TOP_LIKE",
        p.getParticipationId().toString(),
        p.getEventId().toString(),
        p.getStatus().name(),
        p.getHoldStatus().name(),
        p.getScheduledAt(),
        p.getReward(),
        p.getRevision());
  }

  public static AdminRewardPayoutListItemResDto ofBase(BaseRewardPayout p, UUID eventId) {
    return new AdminRewardPayoutListItemResDto(
        p.getId().toString(),
        "BASE",
        p.getParticipationId().toString(),
        eventId == null ? null : eventId.toString(),
        p.getStatus().name(),
        p.getHoldStatus().name(),
        p.getScheduledAt(),
        p.getReward(),
        p.getRevision());
  }
}
