package com.butingbe.domain.travel.ai;

import com.butingbe.domain.place.entity.PlaceTimeSlot;
import com.butingbe.domain.place.service.PlaceDwellTimeProvider;
import com.butingbe.domain.place.service.PlaceTimeSlotProvider;
import com.butingbe.domain.route.HaversineRouteProvider;
import com.butingbe.domain.route.VisitOrderOptimizer;
import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto.WizardPickedPlaceReqDto;
import com.butingbe.domain.travel.entity.TransportType;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.entity.TravelPace;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 선택 장소를 날짜에 나누고 그날의 방문 순서를 정한다.
 *
 * <p>가까운 장소끼리 날짜로 묶는 것은 좌표로 하고, 그 안의 순서와 하루에 담기는 양은 {@link VisitOrderOptimizer}와 이동·체류 시간으로 정한다. 경로
 * 계산을 여기서 새로 만들지 않고 {@code domain.route}의 것을 그대로 쓴다. 리부트가 쓰는 계산과 같은 것이라 두 기능의 결과가 어긋나지 않는다.
 *
 * <p>하루 예산을 넘긴 장소는 다음 날로 넘긴다. 다만 선택 장소는 전부 배치해야 하므로 마지막 날은 넘치더라도 그대로 둔다. 빼는 것은 이 단계의 권한이 아니다.
 */
@Component
@RequiredArgsConstructor
public class TravelPlanRoutePlanner {

  private static final int RELAXED_DAILY_MINUTES = 480;
  private static final int BALANCED_DAILY_MINUTES = 600;
  private static final int TIGHT_DAILY_MINUTES = 720;

  private final VisitOrderOptimizer visitOrderOptimizer;
  private final HaversineRouteProvider haversineRouteProvider;
  private final PlaceDwellTimeProvider placeDwellTimeProvider;
  private final PlaceTimeSlotProvider placeTimeSlotProvider;

  public Map<LocalDate, List<PlaceKey>> plan(
      Travel travel, Map<PlaceKey, WizardPickedPlaceReqDto> catalog) {
    int days =
        Math.toIntExact(ChronoUnit.DAYS.between(travel.getStartDate(), travel.getEndDate()) + 1);
    if (days < 1) {
      throw new TravelPlanValidationException(
          TravelPlanValidationException.Reason.INVALID_SCHEDULE, false, Set.of());
    }

    List<List<PlaceKey>> groups = clusterByProximity(catalog, days);
    List<List<PlaceKey>> scheduled = fitToDailyBudget(groups, catalog, dailyBudgetMinutes(travel));

    Map<LocalDate, List<PlaceKey>> result = new LinkedHashMap<>();
    for (int i = 0; i < days; i++) {
      result.put(travel.getStartDate().plusDays(i), List.copyOf(scheduled.get(i)));
    }
    return java.util.Collections.unmodifiableMap(result);
  }

  /** 좌표가 가까운 장소끼리 날짜 수만큼 묶는다. 균등한 개수보다 권역이 우선이다. */
  private List<List<PlaceKey>> clusterByProximity(
      Map<PlaceKey, WizardPickedPlaceReqDto> catalog, int days) {
    List<List<PlaceKey>> groups = new ArrayList<>();
    List<PlaceKey> unknown = new ArrayList<>();
    catalog.keySet().stream()
        .sorted(Comparator.comparing(PlaceKey::toString))
        .forEach(
            key -> {
              if (located(catalog.get(key))) {
                groups.add(new ArrayList<>(List.of(key)));
              } else {
                unknown.add(key);
              }
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
              diameter = Math.max(diameter, distanceMeters(catalog.get(a), catalog.get(b)));
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
    while (groups.size() < days) {
      groups.add(new ArrayList<>());
    }
    for (PlaceKey key : unknown) {
      groups.stream().min(Comparator.comparingInt(List::size)).orElseThrow().add(key);
    }
    return groups;
  }

  /**
   * 날짜별로 순서를 정하고 하루 예산을 넘는 만큼 다음 날로 넘긴다.
   *
   * <p>마지막 날은 넘길 곳이 없으므로 예산을 넘겨도 그대로 둔다. 선택 장소를 빼지 않는다는 규칙이 우선이다.
   */
  private List<List<PlaceKey>> fitToDailyBudget(
      List<List<PlaceKey>> groups,
      Map<PlaceKey, WizardPickedPlaceReqDto> catalog,
      int budgetMinutes) {
    List<List<PlaceKey>> scheduled = new ArrayList<>();
    List<PlaceKey> carried = new ArrayList<>();

    for (int day = 0; day < groups.size(); day++) {
      List<PlaceKey> candidates = new ArrayList<>(carried);
      candidates.addAll(groups.get(day));
      carried = new ArrayList<>();

      List<PlaceKey> ordered = order(candidates, catalog);
      boolean lastDay = day == groups.size() - 1;
      if (lastDay) {
        scheduled.add(ordered);
        continue;
      }

      List<PlaceKey> kept = new ArrayList<>();
      int spent = 0;
      for (int i = 0; i < ordered.size(); i++) {
        PlaceKey key = ordered.get(i);
        int cost = dwellMinutes(catalog.get(key)) + legMinutes(ordered, i, catalog);
        if (!kept.isEmpty() && spent + cost > budgetMinutes) {
          carried.addAll(ordered.subList(i, ordered.size()));
          break;
        }
        spent += cost;
        kept.add(key);
      }
      scheduled.add(kept);
    }
    return scheduled;
  }

  /** 좌표가 있는 장소는 최적 순서로, 좌표가 없는 장소는 뒤에 붙인다. */
  private List<PlaceKey> order(
      List<PlaceKey> keys, Map<PlaceKey, WizardPickedPlaceReqDto> catalog) {
    List<PlaceKey> unlocated = keys.stream().filter(key -> !located(catalog.get(key))).toList();
    List<PlaceKey> locatedKeys = keys.stream().filter(key -> located(catalog.get(key))).toList();
    if (locatedKeys.size() < 2) {
      List<PlaceKey> ordered = new ArrayList<>(locatedKeys);
      ordered.addAll(unlocated);
      return ordered;
    }

    Map<UUID, PlaceKey> byPointId = new HashMap<>();
    List<RoutePoint> points = new ArrayList<>();
    for (PlaceKey key : locatedKeys) {
      WizardPickedPlaceReqDto place = catalog.get(key);
      UUID pointId = UUID.randomUUID();
      byPointId.put(pointId, key);
      points.add(new RoutePoint(pointId, place.placeName(), place.latitude(), place.longitude()));
    }

    List<PlaceKey> ordered =
        visitOrderOptimizer
            .optimize(null, points, TransportType.PUBLIC_TRANSPORT)
            .orderedPoints()
            .stream()
            .map(point -> byPointId.get(point.placeId()))
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    List<PlaceKey> byTimeSlot = applyTimeSlots(ordered, catalog);
    byTimeSlot.addAll(unlocated);
    return byTimeSlot;
  }

  /**
   * 시간대가 지정된 장소를 앞뒤로 민다. 오전 장소는 앞으로, 저녁 장소는 뒤로 간다.
   *
   * <p>이동 거리 최적화를 버리지 않는다. 같은 시간대 안에서는 최적화가 정한 순서를 그대로 둔다. 시간대가 없는 장소(대다수)도 원래 순서를 지킨다.
   */
  private List<PlaceKey> applyTimeSlots(
      List<PlaceKey> ordered, Map<PlaceKey, WizardPickedPlaceReqDto> catalog) {
    List<PlaceKey> morning = new ArrayList<>();
    List<PlaceKey> unspecified = new ArrayList<>();
    List<PlaceKey> evening = new ArrayList<>();

    for (PlaceKey key : ordered) {
      WizardPickedPlaceReqDto place = catalog.get(key);
      PlaceTimeSlot slot =
          placeTimeSlotProvider.timeSlot(place.provider(), place.providerPlaceId()).orElse(null);
      if (slot == PlaceTimeSlot.MORNING) {
        morning.add(key);
      } else if (slot == PlaceTimeSlot.EVENING) {
        evening.add(key);
      } else {
        unspecified.add(key);
      }
    }

    List<PlaceKey> result = new ArrayList<>(ordered.size());
    result.addAll(morning);
    result.addAll(unspecified);
    result.addAll(evening);
    return result;
  }

  /** 앞 장소에서 이 장소까지의 이동 시간. 첫 장소이거나 좌표가 없으면 0이다. */
  private int legMinutes(
      List<PlaceKey> ordered, int index, Map<PlaceKey, WizardPickedPlaceReqDto> catalog) {
    if (index == 0) {
      return 0;
    }
    WizardPickedPlaceReqDto from = catalog.get(ordered.get(index - 1));
    WizardPickedPlaceReqDto to = catalog.get(ordered.get(index));
    if (!located(from) || !located(to)) {
      return 0;
    }
    RouteLeg leg =
        haversineRouteProvider.leg(
            RoutePoint.of(from.placeName(), from.latitude(), from.longitude()),
            RoutePoint.of(to.placeName(), to.latitude(), to.longitude()),
            TransportType.PUBLIC_TRANSPORT);
    return leg.durationMinutes();
  }

  /** 카탈로그에 적재된 체류 시간을 쓴다. 없는 장소는 provider가 기본값으로 답한다. */
  private int dwellMinutes(WizardPickedPlaceReqDto place) {
    return placeDwellTimeProvider.dwellMinutes(place.provider(), place.providerPlaceId());
  }

  /** 여행 속도를 하루 가용 시간으로 환산한다. 설정이 없으면 보통 속도로 본다. */
  private int dailyBudgetMinutes(Travel travel) {
    TravelPace pace = travel.getPace();
    if (pace == TravelPace.RELAXED) {
      return RELAXED_DAILY_MINUTES;
    }
    if (pace == TravelPace.TIGHT) {
      return TIGHT_DAILY_MINUTES;
    }
    return BALANCED_DAILY_MINUTES;
  }

  /** 직선 이동 거리 합. AI가 돌려준 순서가 서버 순서보다 크게 돌아가는지 비교하는 데 쓴다. */
  public double length(List<PlaceKey> keys, Map<PlaceKey, WizardPickedPlaceReqDto> catalog) {
    double result = 0;
    for (int i = 1; i < keys.size(); i++) {
      result += distanceMeters(catalog.get(keys.get(i - 1)), catalog.get(keys.get(i))) / 1000.0;
    }
    return result;
  }

  public boolean located(WizardPickedPlaceReqDto place) {
    return place.latitude() != null && place.longitude() != null;
  }

  /** 좌표 사이 직선 거리(m). 계산은 {@link HaversineRouteProvider}가 맡는다. */
  private double distanceMeters(WizardPickedPlaceReqDto a, WizardPickedPlaceReqDto b) {
    return haversineRouteProvider
        .leg(
            RoutePoint.of(a.placeName(), a.latitude(), a.longitude()),
            RoutePoint.of(b.placeName(), b.latitude(), b.longitude()),
            TransportType.PUBLIC_TRANSPORT)
        .distanceMeters();
  }
}
