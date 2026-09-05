package com.butingbe.domain.route;

import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.travel.entity.TransportType;
import java.util.List;

/**
 * 지점 사이의 이동 거리와 소요 시간을 제공한다.
 *
 * <p>구현은 외부 경로 API일 수도 있고 좌표만으로 계산하는 근사치일 수도 있다. 호출자는 어느 쪽인지 알 필요가 없다.
 */
public interface RouteProvider {

  /** 두 지점 사이의 구간 하나. */
  RouteLeg leg(RoutePoint from, RoutePoint to, TransportType transportType);

  /** 주어진 순서대로 이동할 때의 구간 목록. 지점이 2개 미만이면 빈 목록을 반환한다. */
  default List<RouteLeg> legs(List<RoutePoint> orderedPoints, TransportType transportType) {
    if (orderedPoints == null || orderedPoints.size() < 2) {
      return List.of();
    }
    return java.util.stream.IntStream.range(0, orderedPoints.size() - 1)
        .mapToObj(i -> leg(orderedPoints.get(i), orderedPoints.get(i + 1), transportType))
        .toList();
  }
}
