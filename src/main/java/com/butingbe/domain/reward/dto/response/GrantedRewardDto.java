package com.butingbe.domain.reward.dto.response;

import com.butingbe.domain.reward.entity.RewardGrant;
import java.time.OffsetDateTime;

/** 지급된 보상 1건. */
public record GrantedRewardDto(
    String grantId,
    String rewardType,
    String code,
    String name,
    Integer pointAmount,
    String grantReason,
    OffsetDateTime grantedAt) {

  public static GrantedRewardDto of(RewardGrant grant, Integer pointAmount) {
    return new GrantedRewardDto(
        grant.getId().toString(),
        grant.getReward().getRewardType().name(),
        grant.getReward().getCode(),
        grant.getReward().getName(),
        pointAmount,
        grant.getGrantReason().name(),
        grant.getGrantedAt());
  }
}
