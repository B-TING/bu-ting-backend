package com.butingbe.domain.reward.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminRewardPayoutMarkReqDto(
    @NotNull UUID payoutId, OffsetDateTime at, String note, @NotNull Long expectedRevision) {}
