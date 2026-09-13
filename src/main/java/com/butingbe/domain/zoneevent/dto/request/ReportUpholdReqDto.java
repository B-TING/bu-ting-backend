package com.butingbe.domain.zoneevent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReportUpholdReqDto(
    @NotBlank String note, @NotNull ReportUpholdAction action, @NotNull Long expectedRevision) {}
