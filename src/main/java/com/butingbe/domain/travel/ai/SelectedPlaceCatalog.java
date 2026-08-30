package com.butingbe.domain.travel.ai;

import static com.butingbe.domain.travel.ai.TravelPlanValidationException.Reason.DUPLICATED_PLACE;
import static com.butingbe.domain.travel.ai.TravelPlanValidationException.Reason.INVALID_PLACE_REFERENCE;

import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto;
import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto.WizardPickedPlaceReqDto;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class SelectedPlaceCatalog {
  private SelectedPlaceCatalog() {}

  public static Map<PlaceKey, WizardPickedPlaceReqDto> from(AiTravelPlanGenerateReqDto request) {
    if (request == null || request.selectedPlaces() == null || request.selectedPlaces().isEmpty()) {
      throw invalid();
    }
    Map<PlaceKey, WizardPickedPlaceReqDto> places = new LinkedHashMap<>();
    for (WizardPickedPlaceReqDto place : request.selectedPlaces()) {
      if (place == null
          || !hasText(place.placeName())
          || !hasText(place.address())
          || !validCoordinate(place.latitude(), 90)
          || !validCoordinate(place.longitude(), 180)) {
        throw invalid();
      }
      PlaceKey key;
      try {
        key = PlaceKey.of(place.provider(), place.providerPlaceId());
      } catch (IllegalArgumentException e) {
        throw invalid();
      }
      if (places.putIfAbsent(key, place) != null) {
        throw new TravelPlanValidationException(DUPLICATED_PLACE, false, Set.of(key));
      }
    }
    return java.util.Collections.unmodifiableMap(places);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank() && value.length() <= 255;
  }

  private static boolean validCoordinate(Double value, double bound) {
    return value == null || Double.isFinite(value) && Math.abs(value) <= bound;
  }

  private static TravelPlanValidationException invalid() {
    return new TravelPlanValidationException(INVALID_PLACE_REFERENCE, false, Set.of());
  }
}
