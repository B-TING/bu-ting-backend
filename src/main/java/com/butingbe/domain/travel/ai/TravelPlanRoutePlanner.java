package com.butingbe.domain.travel.ai;

import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto.WizardPickedPlaceReqDto;
import com.butingbe.domain.travel.entity.Travel;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TravelPlanRoutePlanner {
  public Map<LocalDate, List<PlaceKey>> plan(
      Travel travel, Map<PlaceKey, WizardPickedPlaceReqDto> catalog) {
    int days =
        Math.toIntExact(ChronoUnit.DAYS.between(travel.getStartDate(), travel.getEndDate()) + 1);
    if (days < 1) {
      throw new TravelPlanValidationException(
          TravelPlanValidationException.Reason.INVALID_SCHEDULE, false, Set.of());
    }
    List<List<PlaceKey>> groups = new ArrayList<>();
    List<PlaceKey> unknown = new ArrayList<>();
    catalog.keySet().stream()
        .sorted(Comparator.comparing(PlaceKey::toString))
        .forEach(
            key -> {
              if (located(catalog.get(key))) groups.add(new ArrayList<>(List.of(key)));
              else unknown.add(key);
            });
    // Complete-link clustering avoids joining distant regions just to balance daily counts.
    while (groups.size() > days) {
      int first = 0;
      int second = 1;
      double best = Double.POSITIVE_INFINITY;
      for (int i = 0; i < groups.size(); i++) {
        for (int j = i + 1; j < groups.size(); j++) {
          double diameter = 0;
          for (PlaceKey a : groups.get(i)) {
            for (PlaceKey b : groups.get(j)) {
              diameter = Math.max(diameter, distance(catalog.get(a), catalog.get(b)));
            }
          }
          if (diameter < best) {
            best = diameter;
            first = i;
            second = j;
          }
        }
      }
      groups.get(first).addAll(groups.remove(second));
    }
    while (groups.size() < days) groups.add(new ArrayList<>());
    for (PlaceKey key : unknown) {
      groups.stream().min(Comparator.comparingInt(List::size)).orElseThrow().add(key);
    }
    Map<LocalDate, List<PlaceKey>> result = new LinkedHashMap<>();
    for (int i = 0; i < days; i++) {
      result.put(travel.getStartDate().plusDays(i), route(groups.get(i), catalog));
    }
    return java.util.Collections.unmodifiableMap(result);
  }

  private List<PlaceKey> route(
      List<PlaceKey> keys, Map<PlaceKey, WizardPickedPlaceReqDto> catalog) {
    if (keys.isEmpty() || keys.stream().anyMatch(key -> !located(catalog.get(key)))) {
      return List.copyOf(keys);
    }
    List<PlaceKey> best = List.copyOf(keys);
    double bestLength = length(best, catalog);
    for (PlaceKey start : keys) {
      List<PlaceKey> remaining = new ArrayList<>(keys);
      List<PlaceKey> candidate = new ArrayList<>();
      PlaceKey current = start;
      while (!remaining.isEmpty()) {
        candidate.add(current);
        remaining.remove(current);
        PlaceKey from = current;
        current =
            remaining.stream()
                .min(
                    Comparator.comparingDouble(
                        key -> distance(catalog.get(from), catalog.get(key))))
                .orElse(null);
      }
      double length = length(candidate, catalog);
      if (length < bestLength) {
        best = List.copyOf(candidate);
        bestLength = length;
      }
    }
    return best;
  }

  public double length(List<PlaceKey> keys, Map<PlaceKey, WizardPickedPlaceReqDto> catalog) {
    double result = 0;
    for (int i = 1; i < keys.size(); i++) {
      result += distance(catalog.get(keys.get(i - 1)), catalog.get(keys.get(i)));
    }
    return result;
  }

  public boolean located(WizardPickedPlaceReqDto place) {
    return place.latitude() != null && place.longitude() != null;
  }

  private double distance(WizardPickedPlaceReqDto a, WizardPickedPlaceReqDto b) {
    double latitude = Math.toRadians(b.latitude() - a.latitude());
    double longitude = Math.toRadians(b.longitude() - a.longitude());
    double haversine =
        Math.pow(Math.sin(latitude / 2), 2)
            + Math.cos(Math.toRadians(a.latitude()))
                * Math.cos(Math.toRadians(b.latitude()))
                * Math.pow(Math.sin(longitude / 2), 2);
    return 6371 * 2 * Math.asin(Math.sqrt(Math.min(1, haversine)));
  }
}
