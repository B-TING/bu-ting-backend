package com.butingbe.domain.travel.ai;

import java.util.Set;

public class TravelPlanValidationException extends RuntimeException {
  public enum Reason {
    MISSING_SELECTED_PLACE,
    UNEXPECTED_PLACE,
    DUPLICATED_PLACE,
    INVALID_PLACE_REFERENCE,
    INVALID_SCHEDULE,
    LOW_QUALITY_PLAN
  }

  private final Reason reason;
  private final boolean generatedResponse;
  private final Set<PlaceKey> placeKeys;

  public TravelPlanValidationException(
      Reason reason, boolean generatedResponse, Set<PlaceKey> placeKeys) {
    super("error.travel.ai." + reason.name().toLowerCase(java.util.Locale.ROOT));
    this.reason = reason;
    this.generatedResponse = generatedResponse;
    this.placeKeys = Set.copyOf(placeKeys);
  }

  public Reason getReason() {
    return reason;
  }

  public boolean isGeneratedResponse() {
    return generatedResponse;
  }

  public Set<PlaceKey> getPlaceKeys() {
    return placeKeys;
  }
}
