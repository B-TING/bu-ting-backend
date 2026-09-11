package com.butingbe.domain.zonetitle.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdminZoneTitleCreateReqDto(
    @NotBlank String zoneId,
    @NotNull @Positive Integer tier,
    @NotNull @Positive Integer requiredSuccessCount,
    @NotBlank String titleName,
    @NotBlank String style,
    @NotBlank String color) {}
