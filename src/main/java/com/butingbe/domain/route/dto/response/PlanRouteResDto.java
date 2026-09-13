package com.butingbe.domain.route.dto.response;

import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.travel.entity.TransportType;
import java.util.List;
import java.util.UUID;

/** 한 일자의 이동 경로. 좌표가 없어 계산에서 빠진 장소는 {@code skippedPlaceIds}로 알린다. */
public record PlanRouteResDto(
    UUID planId,
    TransportType transportType,
    List<RouteLeg> legs,
    int totalDistanceMeters,
    int totalDurationMinutes,
    List<UUID> skippedPlaceIds) {

  public static PlanRouteResDto of(
      UUID planId, TransportType transportType, List<RouteLeg> legs, List<UUID> skippedPlaceIds) {
    return new PlanRouteResDto(
        planId,
        transportType,
        legs,
        legs.stream().mapToInt(RouteLeg::distanceMeters).sum(),
        legs.stream().mapToInt(RouteLeg::durationMinutes).sum(),
        List.copyOf(skippedPlaceIds));
  }
}
