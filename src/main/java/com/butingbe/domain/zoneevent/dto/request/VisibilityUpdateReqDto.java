package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 참여 공개 설정 변경 요청. */
public record VisibilityUpdateReqDto(@NotBlank String visibility) {}
