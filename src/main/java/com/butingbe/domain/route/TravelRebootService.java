package com.butingbe.domain.route;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.route.dto.response.TravelRebootResDto;
import com.butingbe.domain.route.dto.response.VisitOrderResDto;
import com.butingbe.domain.travel.entity.Plan;
import com.butingbe.domain.travel.entity.PlanPlace;
import com.butingbe.domain.travel.entity.TransportType;
import com.butingbe.domain.travel.repository.PlanPlaceRepository;
import com.butingbe.domain.travel.repository.PlanRepository;
import com.butingbe.domain.travelteam.service.TravelMemberAuthorization;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재 위치와 남은 시간을 기준으로 그날의 남은 일정을 다시 짠다.
 *
 * <p>경로 계산은 새로 만들지 않고 {@link VisitOrderOptimizer}를 재사용한다(부모 이슈 명시). 리부트가 더하는 것은 두 가지다. 아직 안 간 장소만
 * 추리는 것과, 최적 순서를 이동·체류 시간으로 누적해 남은 시간에 담기는 만큼만 남기는 것.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelRebootService {

  private final PlanRepository planRepository;
  private final PlanPlaceRepository planPlaceRepository;
  private final TravelMemberAuthorization travelMemberAuthorization;
  private final VisitOrderOptimizer visitOrderOptimizer;

  /** 리부트 제안을 계산한다. 저장하지 않는다. */
  public TravelRebootResDto reboot(
      AuthenticatedUser authenticatedUser,
      UUID planId,
      RoutePoint currentPoint,
      int availableMinutes,
      TransportType transportType) {
    UUID userId = requireUserId(authenticatedUser);
    Plan plan = findPlan(planId);
    travelMemberAuthorization.validateMember(plan.getTravel().getId(), userId);

    TransportType mode = transportType == null ? TransportType.PUBLIC_TRANSPORT : transportType;

    List<PlanPlace> places = planPlaceRepository.findByPlan_IdOrderBySequenceAsc(planId);
    List<UUID> visited = new ArrayList<>();
    List<UUID> skipped = new ArrayList<>();
    List<RoutePoint> unvisitedPoints = new ArrayList<>();
    Map<UUID, Integer> stayMinutesByPlaceId = new LinkedHashMap<>();

    for (PlanPlace place : places) {
      if (Boolean.TRUE.equals(place.getVisited())) {
        visited.add(place.getId());
        continue;
      }
      if (place.getLatitude() == null || place.getLongitude() == null) {
        skipped.add(place.getId());
        continue;
      }
      unvisitedPoints.add(
          new RoutePoint(
              place.getId(), place.getPlaceName(), place.getLatitude(), place.getLongitude()));
      stayMinutesByPlaceId.put(
          place.getId(), place.getDurationMinutes() == null ? 0 : place.getDurationMinutes());
    }

    VisitOrderResDto optimized = visitOrderOptimizer.optimize(currentPoint, unvisitedPoints, mode);

    return trimToAvailableTime(
        mode, optimized, stayMinutesByPlaceId, availableMinutes, visited, skipped);
  }

  /**
   * 최적 순서를 이동·체류 시간으로 누적하며 남은 시간에 담기는 지점까지 자른다.
   *
   * <p>{@code orderedPoints}는 [현재 위치, 장소1, 장소2, ...] 꼴이고, {@code legs}는 그 사이 구간이다. 각 장소를 넣을 때 그 앞
   * 구간의 이동 시간과 그 장소의 체류 시간을 더해, 합이 남은 시간을 넘으면 그 장소부터는 시간 부족으로 제외한다.
   */
  private TravelRebootResDto trimToAvailableTime(
      TransportType transportType,
      VisitOrderResDto optimized,
      Map<UUID, Integer> stayMinutesByPlaceId,
      int availableMinutes,
      List<UUID> visitedPlaceIds,
      List<UUID> skippedPlaceIds) {
    List<RoutePoint> ordered = optimized.orderedPoints();
    List<RouteLeg> legs = optimized.legs();

    List<RoutePoint> keptPoints = new ArrayList<>();
    List<RouteLeg> keptLegs = new ArrayList<>();
    List<UUID> reachable = new ArrayList<>();
    List<UUID> droppedForTime = new ArrayList<>();

    int travelMinutes = 0;
    int stayMinutes = 0;

    if (!ordered.isEmpty()) {
      // 현재 위치(첫 지점)는 이동·체류 없이 그대로 시작점으로 둔다.
      keptPoints.add(ordered.get(0));
    }

    boolean overBudget = false;
    for (int i = 1; i < ordered.size(); i++) {
      RoutePoint place = ordered.get(i);
      int legMinutes = legs.get(i - 1).durationMinutes();
      int placeStay = stayMinutesByPlaceId.getOrDefault(place.placeId(), 0);

      if (overBudget || travelMinutes + stayMinutes + legMinutes + placeStay > availableMinutes) {
        overBudget = true;
        droppedForTime.add(place.placeId());
        continue;
      }

      travelMinutes += legMinutes;
      stayMinutes += placeStay;
      keptPoints.add(place);
      keptLegs.add(legs.get(i - 1));
      reachable.add(place.placeId());
    }

    return new TravelRebootResDto(
        transportType,
        List.copyOf(keptPoints),
        List.copyOf(keptLegs),
        travelMinutes,
        stayMinutes,
        travelMinutes + stayMinutes,
        availableMinutes,
        List.copyOf(reachable),
        List.copyOf(droppedForTime),
        List.copyOf(visitedPlaceIds),
        List.copyOf(skippedPlaceIds));
  }

  private UUID requireUserId(AuthenticatedUser authenticatedUser) {
    if (authenticatedUser == null || authenticatedUser.id() == null) {
      throw new UnauthenticatedException();
    }
    return authenticatedUser.id();
  }

  private Plan findPlan(UUID planId) {
    return planRepository
        .findById(planId)
        .orElseThrow(() -> new ResourceNotFoundException("Plan not found."));
  }
}
