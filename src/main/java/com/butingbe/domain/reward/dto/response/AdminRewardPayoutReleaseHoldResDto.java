package com.butingbe.domain.reward.dto.response;

/** payoutType은 TOP_LIKE(RewardPayout) 또는 BASE(BaseRewardPayout). */
public record AdminRewardPayoutReleaseHoldResDto(
    String payoutId, String payoutType, String holdStatus, Long revision) {}
