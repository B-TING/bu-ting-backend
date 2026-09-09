package com.butingbe.domain.reward.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

public record AdminRewardPayoutBulkConfirmReqDto(
    @NotEmpty List<String> payoutIds, Map<String, Long> expectedRevisions) {}
