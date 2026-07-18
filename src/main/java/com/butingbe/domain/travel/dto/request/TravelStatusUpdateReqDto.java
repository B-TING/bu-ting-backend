package com.butingbe.domain.travel.dto.request;

import com.butingbe.domain.travel.entity.TravelStatus;
import jakarta.validation.constraints.NotNull;

public record TravelStatusUpdateReqDto(
    @NotNull(message = "Travel status is required.") TravelStatus status) {}
