package com.butingbe.domain.travel.ai;

import java.time.LocalDate;
import java.util.List;

public record TravelPlanAiResponse(List<Day> days) {
  public record Day(LocalDate date, List<Place> places) {}

  public record Place(int order, String name, String description, String recommendationReason) {}
}
