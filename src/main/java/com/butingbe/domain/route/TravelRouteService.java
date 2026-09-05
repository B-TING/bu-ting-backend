package com.butingbe.domain.route;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.route.dto.response.AlternativeRouteResDto;
import com.butingbe.domain.route.dto.response.PlanRouteResDto;
import com.butingbe.domain.route.dto.response.TravelRouteOptimizeResDto;
import com.butingbe.domain.route.dto.response.VisitOrderResDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceSequenceUpdateReqDto;
import com.butingbe.domain.travel.dto.response.PlanPlaceResDto;
import com.butingbe.domain.travel.entity.Plan;
import com.butingbe.domain.travel.entity.PlanPlace;
import com.butingbe.domain.travel.entity.TransportType;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.repository.PlanPlaceRepository;
import com.butingbe.domain.travel.repository.PlanRepository;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travel.service.TravelService;
import com.butingbe.domain.travelteam.service.TravelMemberAuthorization;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private final TravelService travelService;

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

  /**
   * 못 가게 된 장소를 빼고 대체 경로를 만든다. 저장하지는 않고 제안만 돌려준다.
   *
   * <p>제외한 장소와 좌표가 없어 빠진 장소를 뺀 나머지를 다시 최적화한다. 기존 경로(아무것도 빼지 않은 입력 순서)의 시간을 함께 계산해, 대체 경로가 얼마나 짧아졌는지
   * 견줄 수 있게 한다.
   */
  public AlternativeRouteResDto generateAlternativeRoute(
      AuthenticatedUser authenticatedUser,
      UUID planId,
      List<UUID> excludePlaceIds,
      RoutePoint start,
      TransportType transportType) {
    UUID userId = requireUserId(authenticatedUser);
    Plan plan = findPlan(planId);
    travelMemberAuthorization.validateMember(plan.getTravel().getId(), userId);

    TransportType mode = transportType == null ? TransportType.PUBLIC_TRANSPORT : transportType;
    List<UUID> excluded = excludePlaceIds == null ? List.of() : excludePlaceIds;
    LocatedPlaces located = locatedPoints(planId);

    // 기존 경로: 아무것도 빼지 않은 입력 순서 그대로의 이동 시간.
    int originalDuration =
        routeProvider.legs(located.points(), mode).stream()
            .mapToInt(RouteLeg::durationMinutes)
            .sum();

    List<RoutePoint> remaining =
        located.points().stream().filter(point -> !excluded.contains(point.placeId())).toList();

    VisitOrderResDto alternative = visitOrderOptimizer.optimize(start, remaining, mode);
    return AlternativeRouteResDto.of(
        mode, alternative, originalDuration, excluded, located.skippedPlaceIds());
  }

  /**
   * 최적화한 순서를 일정에 실제로 반영한다.
   *
   * <p>최적화는 좌표가 있는 장소만 다루므로 요청에 일정의 모든 장소가 들어오지는 않는다. 빠진 장소를 그대로 두면 일정에서 사라지므로, **기존 순서를 유지한 채 뒤에
   * 붙여** 하나도 잃지 않게 한다.
   *
   * <p>실제 순서 쓰기는 {@link TravelService#updatePlanPlaceSequence}에 위임한다. 중복·소속 검증과 임시 순번을 거치는 갱신이 이미
   * 거기 있고, 순서를 바꾸는 경로가 둘로 갈라지면 규칙이 어긋나기 쉽다.
   */
  @Transactional
  public List<PlanPlaceResDto> applyOptimizedOrder(
      AuthenticatedUser authenticatedUser, UUID planId, List<UUID> optimizedPlaceIds) {
    UUID userId = requireUserId(authenticatedUser);
    Plan plan = findPlan(planId);
    travelMemberAuthorization.validateMember(plan.getTravel().getId(), userId);

    List<PlanPlace> places = planPlaceRepository.findByPlan_IdOrderBySequenceAsc(planId);
    List<UUID> requested = optimizedPlaceIds == null ? List.of() : optimizedPlaceIds;

    validateBelongsToPlan(places, requested);

    List<UUID> fullOrder = new ArrayList<>(requested);
    places.stream()
        .map(PlanPlace::getId)
        .filter(id -> !fullOrder.contains(id))
        .forEach(fullOrder::add);

    return travelService.updatePlanPlaceSequence(
        authenticatedUser, planId, new PlanPlaceSequenceUpdateReqDto(fullOrder));
  }

  /** 요청한 장소가 모두 이 일정의 것인지, 중복이 없는지 확인한다. 누락은 허용한다. */
  private void validateBelongsToPlan(List<PlanPlace> places, List<UUID> requestedIds) {
    if (new HashSet<>(requestedIds).size() != requestedIds.size()) {
      throw new IllegalArgumentException("Duplicated plan place id exists.");
    }
    Set<UUID> planPlaceIds = places.stream().map(PlanPlace::getId).collect(Collectors.toSet());
    if (!planPlaceIds.containsAll(requestedIds)) {
      throw new IllegalArgumentException("Plan place ids do not match this plan.");
    }
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
