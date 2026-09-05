package com.butingbe.domain.route;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.route.dto.response.PlanRouteResDto;
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

    return visitOrderOptimizer.optimize(start, locatedPoints(planId).points(), transportType);
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

  private Plan findPlan(UUID planId) {
    return planRepository
        .findById(planId)
        .orElseThrow(() -> new ResourceNotFoundException("Plan not found."));
  }
}
