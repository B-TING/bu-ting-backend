package com.butingbe.domain.route;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.route.dto.response.PlanRouteResDto;
import com.butingbe.domain.route.dto.response.TravelRouteOptimizeResDto;
import com.butingbe.domain.route.dto.response.VisitOrderResDto;
import com.butingbe.domain.travel.entity.Plan;
import com.butingbe.domain.travel.entity.PlanPlace;
import com.butingbe.domain.travel.entity.TransportType;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.repository.PlanPlaceRepository;
import com.butingbe.domain.travel.repository.PlanRepository;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travelteam.service.TravelMemberAuthorization;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 여행 일정의 장소들을 좌표로 풀어 이동 경로와 소요 시간을 계산한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelRouteService {

  private final TravelRepository travelRepository;
  private final PlanRepository planRepository;
  private final PlanPlaceRepository planPlaceRepository;
  private final TravelMemberAuthorization travelMemberAuthorization;
  private final RouteProvider routeProvider;
  private final VisitOrderOptimizer visitOrderOptimizer;

  /** 일정에 담긴 순서대로 이동할 때의 구간과 합계를 반환한다. */
  public PlanRouteResDto getPlanRoute(
      AuthenticatedUser authenticatedUser, UUID planId, TransportType transportType) {
    UUID userId = requireUserId(authenticatedUser);
    Plan plan = findPlan(planId);
    travelMemberAuthorization.validateMember(plan.getTravel().getId(), userId);

    LocatedPlaces located = locatedPoints(planId);
    TransportType mode = transportType == null ? TransportType.PUBLIC_TRANSPORT : transportType;
    List<RouteLeg> legs = routeProvider.legs(located.points(), mode);

    return PlanRouteResDto.of(planId, mode, legs, located.skippedPlaceIds());
  }

  /** 일정의 장소 중 좌표가 있는 것만 순서대로 모으고, 좌표가 없어 빠진 장소는 따로 남긴다. */
  private LocatedPlaces locatedPoints(UUID planId) {
    List<RoutePoint> points = new ArrayList<>();
    List<UUID> skippedPlaceIds = new ArrayList<>();
    for (PlanPlace place : planPlaceRepository.findByPlan_IdOrderBySequenceAsc(planId)) {
      if (place.getLatitude() == null || place.getLongitude() == null) {
        skippedPlaceIds.add(place.getId());
        continue;
      }
      points.add(
          new RoutePoint(
              place.getId(), place.getPlaceName(), place.getLatitude(), place.getLongitude()));
    }
    return new LocatedPlaces(points, skippedPlaceIds);
  }

  private record LocatedPlaces(List<RoutePoint> points, List<UUID> skippedPlaceIds) {}

  /**
   * 일정의 방문 순서를 최적화한 결과를 계산한다. 저장하지는 않고 제안만 돌려준다.
   *
   * @param start 출발 지점. {@code null}이면 일정의 첫 장소에서 시작한다.
   */
  public VisitOrderResDto optimizeVisitOrder(
      AuthenticatedUser authenticatedUser,
      UUID planId,
      RoutePoint start,
      TransportType transportType) {
    UUID userId = requireUserId(authenticatedUser);
    Plan plan = findPlan(planId);
    travelMemberAuthorization.validateMember(plan.getTravel().getId(), userId);

    LocatedPlaces located = locatedPoints(planId);
    return withSkipped(
        visitOrderOptimizer.optimize(start, located.points(), transportType),
        located.skippedPlaceIds());
  }

  /**
   * 여행 전체의 방문 순서를 일자별로 최적화한다. 저장하지는 않고 제안만 돌려준다.
   *
   * <p>일자를 하나의 경로로 이어 붙이지 않는다. 하루가 끝나면 숙소로 돌아가고 다음 날 다시 나서므로, 전체를 한 경로로 묶으면 마지막 장소에서 다음 날 첫 장소로
   * 이동하는 비용이 끼어들어 실제와 다른 순서가 나온다.
   */
  public TravelRouteOptimizeResDto optimizeTravelVisitOrder(
      AuthenticatedUser authenticatedUser, UUID travelId, TransportType transportType) {
    UUID userId = requireUserId(authenticatedUser);
    findTravel(travelId);
    travelMemberAuthorization.validateMember(travelId, userId);

    TransportType mode = transportType == null ? TransportType.PUBLIC_TRANSPORT : transportType;
    List<TravelRouteOptimizeResDto.DayRoute> days =
        planRepository.findByTravel_IdOrderByDayNumberAsc(travelId).stream()
            .map(
                plan -> {
                  LocatedPlaces located = locatedPoints(plan.getId());
                  VisitOrderResDto route =
                      withSkipped(
                          visitOrderOptimizer.optimize(null, located.points(), mode),
                          located.skippedPlaceIds());
                  return TravelRouteOptimizeResDto.DayRoute.of(
                      plan.getId(), plan.getDayNumber(), plan.getVisitDate(), route);
                })
            .toList();

    return TravelRouteOptimizeResDto.of(travelId, mode, days);
  }

  /** 최적화 결과에 좌표가 없어 빠진 장소를 덧붙인다. 순서 계산 자체는 좌표가 있는 장소만 다룬다. */
  private VisitOrderResDto withSkipped(VisitOrderResDto route, List<UUID> skippedPlaceIds) {
    if (skippedPlaceIds.isEmpty()) {
      return route;
    }
    return VisitOrderResDto.of(
        route.transportType(),
        route.orderedPoints(),
        route.legs(),
        route.originalDurationMinutes(),
        skippedPlaceIds);
  }

  /** 임의의 두 지점 사이 구간. 현재 위치에서 다음 장소까지처럼 일정 밖의 조회에 쓴다. */
  public RouteLeg getLeg(
      AuthenticatedUser authenticatedUser,
      RoutePoint from,
      RoutePoint to,
      TransportType transportType) {
    requireUserId(authenticatedUser);
    return routeProvider.leg(
        from, to, transportType == null ? TransportType.PUBLIC_TRANSPORT : transportType);
  }

  private UUID requireUserId(AuthenticatedUser authenticatedUser) {
    if (authenticatedUser == null || authenticatedUser.id() == null) {
      throw new UnauthenticatedException();
    }
    return authenticatedUser.id();
  }

  private Travel findTravel(UUID travelId) {
    return travelRepository
        .findById(travelId)
        .orElseThrow(() -> new ResourceNotFoundException("Travel not found."));
  }

  private Plan findPlan(UUID planId) {
    return planRepository
        .findById(planId)
        .orElseThrow(() -> new ResourceNotFoundException("Plan not found."));
  }
}
