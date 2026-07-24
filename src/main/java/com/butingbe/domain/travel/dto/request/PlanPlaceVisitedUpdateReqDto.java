package com.butingbe.domain.travel.dto.request;

import jakarta.validation.constraints.NotNull;

public record PlanPlaceVisitedUpdateReqDto(
    @NotNull(message = "Visited is required.") Boolean visited) {}
