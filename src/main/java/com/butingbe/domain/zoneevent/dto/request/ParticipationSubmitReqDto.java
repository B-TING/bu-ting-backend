package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 인증 제출 요청. 선택한 타겟, 미디어 fileKey, 촬영 시점 좌표. 반려 후 재제출 시 다른 타겟을 고를 수 있다. */
public record ParticipationSubmitReqDto(
    @NotNull UUID targetId,
    @NotBlank String mediaFileKey,
    @Size(max = 300) String content,
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
    OffsetDateTime capturedAt) {}
