package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** 참여 시작 요청. 선택한 타겟과 현재 GPS 좌표. */
public record ParticipationJoinReqDto(
    @NotNull UUID targetId,
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) {}
