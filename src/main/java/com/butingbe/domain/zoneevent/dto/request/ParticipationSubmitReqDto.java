package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** 인증 제출 요청. 미디어 fileKey와 촬영 시점 좌표. */
public record ParticipationSubmitReqDto(
    @NotBlank String mediaFileKey,
    @Size(max = 300) String content,
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
    OffsetDateTime capturedAt) {}
