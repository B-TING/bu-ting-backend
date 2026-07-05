package com.butingbe.domain.travel.dto.request;

import com.butingbe.domain.travel.entity.PlaceProvider;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlanPlaceCreateReqDto(
    @Min(value = 1, message = "Sequence must be greater than 0.") Integer sequence,
    @NotBlank(message = "Place name is required.") String placeName,
    @NotBlank(message = "Address is required.") String address,
    Double latitude,
    Double longitude,
    @NotNull(message = "Place provider is required.") PlaceProvider provider,
    @NotBlank(message = "Provider place id is required.") String providerPlaceId,
    @Min(value = 0, message = "Duration minutes cannot be negative.") Integer durationMinutes,
    Boolean visited) {}
