package com.butingbe.domain.route.dto.response;

import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.travel.entity.TransportType;
import java.util.List;

/**
 * 최적화된 방문 순서와 그 근거.
 *
 * <p>{@code originalDurationMinutes}는 입력 순서 그대로 이동했을 때의 시간이다. 최적화가 실제로 도움이 되었는지 호출자가 판단할 수 있도록 함께
 * 돌려준다. 이미 최적이면 {@code savedMinutes}가 0이다.
 */
public record VisitOrderResDto(
    TransportType transportType,
    List<RoutePoint> orderedPoints,
    List<RouteLeg> legs,
    int totalDistanceMeters,
    int totalDurationMinutes,
    int originalDurationMinutes,
    int savedMinutes) {

  public static VisitOrderResDto of(
      TransportType transportType,
      List<RoutePoint> orderedPoints,
      List<RouteLeg> legs,
      int originalDurationMinutes) {
    int optimized = legs.stream().mapToInt(RouteLeg::durationMinutes).sum();
    return new VisitOrderResDto(
        transportType,
        List.copyOf(orderedPoints),
        List.copyOf(legs),
        legs.stream().mapToInt(RouteLeg::distanceMeters).sum(),
        optimized,
        originalDurationMinutes,
        Math.max(0, originalDurationMinutes - optimized));
  }
}
