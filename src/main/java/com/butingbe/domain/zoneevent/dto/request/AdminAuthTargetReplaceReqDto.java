package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 인증 타겟 긴급 교체. 기존 타겟은 REPLACED로, 새 contentId 기준 타겟이 ACTIVE로 생성된다. */
public record AdminAuthTargetReplaceReqDto(
    @NotNull String placeContentId,
    @NotNull String contentTypeId,
    String guideText,
    String exampleFileKey,
    @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
    @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
    @NotNull @Min(30) @Max(500) Integer radiusM,
    String reason) {}
