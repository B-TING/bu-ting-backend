package com.butingbe.domain.travel.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PlanCreateReqDto(
    @NotNull(message = "Day number is required.")
        @Min(value = 1, message = "Day number must be greater than 0.")
        Integer dayNumber,
    @NotNull(message = "Visit date is required.") LocalDate visitDate) {}
