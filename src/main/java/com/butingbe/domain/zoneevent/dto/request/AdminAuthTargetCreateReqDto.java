package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 인증 타겟 추가. PLACE는 placeContentId/contentTypeId 필수, OBJECT는 landmarkId/placeName 필수. */
public record AdminAuthTargetCreateReqDto(
    @NotNull String targetKind,
    String landmarkId,
    String placeContentId,
    String contentTypeId,
    String placeName,
    String guideText,
    String exampleFileKey,
    @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
    @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
    @NotNull @Min(30) @Max(500) Integer radiusM) {}
