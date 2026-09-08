package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 인증 타겟 부분 수정. null 필드는 변경하지 않는다. latitude/longitude는 쌍으로만 허용. */
public record AdminAuthTargetPatchReqDto(
    String guideText,
    String exampleFileKey,
    @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
    @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
    @Min(30) @Max(500) Integer radiusM,
    String reason,
    @NotNull Long expectedRevision) {}
