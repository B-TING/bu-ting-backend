package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** 검수 반려 요청. */
public record ReviewRejectReqDto(
    @NotNull UUID submissionId, @NotBlank String reason, @NotNull Long expectedRevision) {}
