package com.butingbe.domain.reward.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReleaseHoldReqDto(@NotBlank String note, @NotNull Long expectedRevision) {}
