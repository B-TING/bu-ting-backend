package com.butingbe.domain.reward.dto.request;

import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record AdminRewardPayoutUpdateReqDto(
    RewardSnapshot reward,
    String memo,
    OffsetDateTime scheduledAt,
    @NotNull Long expectedRevision) {}
