package com.butingbe.domain.reward.dto.request;

import jakarta.validation.constraints.NotNull;

public record AdminRewardPayoutRetryReqDto(String note, @NotNull Long expectedRevision) {}
