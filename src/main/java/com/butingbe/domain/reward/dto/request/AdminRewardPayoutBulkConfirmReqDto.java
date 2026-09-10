package com.butingbe.domain.reward.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record AdminRewardPayoutBulkConfirmReqDto(
    @NotEmpty List<String> payoutIds, @NotNull Map<String, Long> expectedRevisions) {}
