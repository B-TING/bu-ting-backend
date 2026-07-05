package com.butingbe.domain.travel.dto.request;

import java.time.LocalTime;

public record PlanPlaceUpdateReqDto(String memo, LocalTime scheduledTime) {}
