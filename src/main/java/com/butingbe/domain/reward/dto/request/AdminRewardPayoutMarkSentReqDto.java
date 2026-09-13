package com.butingbe.domain.reward.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminRewardPayoutMarkSentReqDto(
    @NotNull UUID payoutId,
    OffsetDateTime sentAt,
    String reference,
    String note,
    @NotNull Long expectedRevision) {}
