package com.butingbe.domain.travel.dto.request;

import jakarta.validation.constraints.Min;
import java.time.LocalTime;

public record PlanPlaceUpdateReqDto(
    @Min(value = 0, message = "Duration minutes cannot be negative.") Integer durationMinutes,
    LocalTime scheduledTime,
    String memo) {}
