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
    if (request == null) {
      throw invalid();
    }
    return fromPlaces(request.selectedPlaces());
  }

  /**
   * 사용자가 고른 장소와 서버가 채운 후보를 합쳐 카탈로그를 만든다.
   *
   * <p>앞에 오는 목록이 우선이다. 같은 장소가 양쪽에 있으면 사용자가 고른 쪽이 남는다.
   */
  public static Map<PlaceKey, WizardPickedPlaceReqDto> fromPlaces(
      java.util.List<WizardPickedPlaceReqDto> selected) {
    if (selected == null || selected.isEmpty()) {
      throw invalid();
    }
    Map<PlaceKey, WizardPickedPlaceReqDto> places = new LinkedHashMap<>();
    for (WizardPickedPlaceReqDto place : selected) {
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
