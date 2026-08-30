package com.butingbe.domain.travel.ai;

import static com.butingbe.domain.travel.ai.TravelPlanValidationException.Reason.DUPLICATED_PLACE;
import static com.butingbe.domain.travel.ai.TravelPlanValidationException.Reason.INVALID_PLACE_REFERENCE;
import static com.butingbe.domain.travel.ai.TravelPlanValidationException.Reason.INVALID_SCHEDULE;
import static com.butingbe.domain.travel.ai.TravelPlanValidationException.Reason.MISSING_SELECTED_PLACE;
import static com.butingbe.domain.travel.ai.TravelPlanValidationException.Reason.UNEXPECTED_PLACE;

import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto.WizardPickedPlaceReqDto;
import com.butingbe.domain.travel.entity.Travel;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TravelPlanAiResponseValidator {
  public void validate(
      Travel travel,
      TravelPlanAiResponse response,
      Map<PlaceKey, WizardPickedPlaceReqDto> requested) {
    long expectedDays = ChronoUnit.DAYS.between(travel.getStartDate(), travel.getEndDate()) + 1;
    if (expectedDays < 1
        || response == null
        || response.days() == null
        || response.days().size() != expectedDays) throw failure(INVALID_SCHEDULE, Set.of());
    Set<LocalDate> dates = new HashSet<>();
    Set<PlaceKey> generated = new HashSet<>();
    Set<PlaceKey> duplicates = new HashSet<>();
    for (TravelPlanAiResponse.Day day : response.days()) {
      if (day == null
          || day.date() == null
          || !dates.add(day.date())
          || day.date().isBefore(travel.getStartDate())
          || day.date().isAfter(travel.getEndDate())) {
        throw failure(INVALID_SCHEDULE, Set.of());
      }
      if (day.places() == null) throw failure(INVALID_SCHEDULE, Set.of());
      Set<Integer> orders = new HashSet<>();
      for (TravelPlanAiResponse.Place place : day.places()) {
        if (place == null
            || place.order() < 1
            || !orders.add(place.order())
            || place.order() != orders.size()
            || place.memo() == null
            || place.memo().isBlank()) {
          throw failure(INVALID_SCHEDULE, Set.of());
        }
        PlaceKey key;
        try {
          key = PlaceKey.of(place.provider(), place.providerPlaceId());
        } catch (IllegalArgumentException e) {
          throw failure(INVALID_PLACE_REFERENCE, Set.of());
        }
        if (!generated.add(key)) duplicates.add(key);
      }
    }
    Set<PlaceKey> missing = new HashSet<>(requested.keySet());
    missing.removeAll(generated);
    Set<PlaceKey> unexpected = new HashSet<>(generated);
    unexpected.removeAll(requested.keySet());
    if (!duplicates.isEmpty()) throw failure(DUPLICATED_PLACE, duplicates);
    if (!unexpected.isEmpty()) throw failure(UNEXPECTED_PLACE, unexpected);
    if (!missing.isEmpty()) throw failure(MISSING_SELECTED_PLACE, missing);
  }

  private TravelPlanValidationException failure(
      TravelPlanValidationException.Reason reason, Set<PlaceKey> keys) {
    return new TravelPlanValidationException(reason, true, keys);
  }
}
