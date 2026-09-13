package com.butingbe.domain.zonetitle.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AdminZoneTitleCreateReqDto(
    @NotBlank String zoneId,
    @NotNull @Min(1) @Max(3) Integer tier,
    @NotNull @Positive Integer requiredSuccessCount,
    @NotBlank @Size(max = 100) String titleName,
    @NotBlank @Size(max = 20) String style,
    @NotBlank @Size(max = 20) String color) {}
