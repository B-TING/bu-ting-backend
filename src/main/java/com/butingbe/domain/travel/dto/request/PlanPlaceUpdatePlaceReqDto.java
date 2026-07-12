package com.butingbe.domain.travel.dto.request;

import com.butingbe.domain.travel.entity.PlaceProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlanPlaceUpdatePlaceReqDto(
    @NotBlank(message = "Place name is required.") String placeName,
    @NotBlank(message = "Address is required.") String address,
    Double latitude,
    Double longitude,
    @NotNull(message = "Place provider is required.") PlaceProvider provider,
    @NotBlank(message = "Provider place id is required.") String providerPlaceId) {}
