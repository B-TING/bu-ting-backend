package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 신고 요청. */
public record ReportReqDto(@NotBlank String reasonCode, @Size(max = 500) String memo) {}
