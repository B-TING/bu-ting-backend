package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 인증 타겟 입력. */
public record AuthTargetReqDto(
    @NotNull String targetKind,
    String landmarkId,
    @NotNull String placeName,
    String guideText,
    String exampleFileKey,
    @NotNull Double latitude,
    @NotNull Double longitude,
    @NotNull @Min(30) @Max(500) Integer radiusM) {}
