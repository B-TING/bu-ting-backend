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

  /**
   * 모든 지점 쌍 사이의 소요 시간(분) 행렬. {@code [i][j]}는 i에서 j로 갈 때의 시간이다.
   *
   * <p>기본 구현은 쌍마다 {@link #leg}를 호출한다. 외부 API 어댑터는 이 메서드를 재정의해 한 번의 행렬 조회로 대체할 수 있다. 순서 최적화는 O(n²)
   * 쌍을 필요로 하므로, 재정의하지 않으면 지점 수의 제곱만큼 외부 호출이 나간다.
   */
  default int[][] durationMatrixMinutes(List<RoutePoint> points, TransportType transportType) {
    int size = points.size();
    int[][] matrix = new int[size][size];
    for (int from = 0; from < size; from++) {
      for (int to = 0; to < size; to++) {
        matrix[from][to] =
            from == to ? 0 : leg(points.get(from), points.get(to), transportType).durationMinutes();
      }
    }
    return matrix;
  }
}
