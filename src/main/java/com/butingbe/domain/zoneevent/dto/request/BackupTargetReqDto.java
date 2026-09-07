package com.butingbe.domain.zoneevent.dto.request;

import com.butingbe.domain.zoneevent.entity.ZoneEventTargetKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 우천 대체용 예비 타겟 등록. */
public record BackupTargetReqDto(
    @NotNull ZoneEventTargetKind targetKind,
    String landmarkId,
    @NotBlank String placeName,
    String guideText,
    String exampleFileKey,
    @NotNull Double latitude,
    @NotNull Double longitude,
    @NotNull @Positive Integer radiusM) {}
