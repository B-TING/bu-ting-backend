package com.butingbe.domain.travel.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AiTravelPlanGenerateReqDto(
    @NotEmpty @Valid List<WizardPickedPlaceReqDto> selectedPlaces,
    List<String> foodIds,
    String schedulePace,
    List<String> purposes,
    String bookedAccommodation,
    List<String> accommodationAreaIds) {
  public record WizardPickedPlaceReqDto(
      @NotNull String provider,
      @NotNull String providerPlaceId,
      @NotNull String placeName,
      String address,
      Double latitude,
      Double longitude,
      String type) {}
}
