package com.butingbe.domain.travel.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;

public record TravelPlanAiResponse(List<Day> days) {
  public record Day(LocalDate date, List<Place> places) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Place(int order, String provider, String providerPlaceId, String memo) {}
}
