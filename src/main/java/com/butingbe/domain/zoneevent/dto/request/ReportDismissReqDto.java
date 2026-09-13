package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReportDismissReqDto(@NotBlank String note, @NotNull Long expectedRevision) {}
