package com.butingbe.domain.reward.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record AdminRewardPayoutBulkScheduleReqDto(
    @NotEmpty List<String> payoutIds,
    @NotNull OffsetDateTime scheduledAt,
    @NotNull Map<String, Long> expectedRevisions) {}
