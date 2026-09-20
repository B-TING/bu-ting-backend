package com.butingbe.domain.zoneevent.dto.request;

import com.butingbe.domain.zoneevent.entity.ZoneEventTargetKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 우천 대체용 예비 타겟 등록. */
public record BackupTargetReqDto(
    @NotNull ZoneEventTargetKind targetKind,
    String landmarkId,
    @NotBlank String placeName,
    String guideText,
    String exampleFileKey,
    @NotNull Double latitude,
    @NotNull Double longitude,
    @NotNull @Min(30) @Max(2000) Integer radiusM) {}
