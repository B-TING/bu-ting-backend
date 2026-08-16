package com.butingbe.domain.travel.ai;

import com.butingbe.domain.travel.entity.Travel;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TravelPlanAiResponseValidator {
  public void validate(Travel travel, TravelPlanAiResponse response) {
    if (response == null || response.days() == null)
      throw new IllegalArgumentException("AI response days are required.");
    Set<LocalDate> dates = new HashSet<>();
    for (TravelPlanAiResponse.Day day : response.days()) {
      if (day == null
          || day.date() == null
          || !dates.add(day.date())
          || day.date().isBefore(travel.getStartDate())
          || day.date().isAfter(travel.getEndDate())) {
        throw new IllegalArgumentException("AI response contains an invalid date.");
      }
      if (day.places() == null)
        throw new IllegalArgumentException("AI response places are required.");
      Set<Integer> orders = new HashSet<>();
      for (TravelPlanAiResponse.Place place : day.places()) {
        if (place == null
            || place.order() < 1
            || !orders.add(place.order())
            || place.name() == null
            || place.name().isBlank()) {
          throw new IllegalArgumentException("AI response contains an invalid place.");
        }
      }
    }
  }
}
