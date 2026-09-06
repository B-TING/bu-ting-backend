package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 검수 반려 요청. */
public record RejectReqDto(@NotBlank String failReason) {}
