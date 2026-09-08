package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 회차 긴급 취소. 연결된 구역 슬롯도 함께 취소되지만 기존 참여·검수·보상 이력은 유지된다. */
public record RoundCancelReqDto(@NotBlank String reason, @NotNull Long expectedRevision) {}
