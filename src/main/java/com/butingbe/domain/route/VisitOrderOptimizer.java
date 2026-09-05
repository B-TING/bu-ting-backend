package com.butingbe.domain.route;

import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.route.dto.response.VisitOrderResDto;
import com.butingbe.domain.travel.entity.TransportType;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 이동 시간이 가장 짧아지는 방문 순서를 찾는다.
 *
 * <p>돌아오지 않는 열린 경로를 다루므로 순회 외판원이 아니라 해밀턴 경로 문제다. 출발 지점이 주어지면 그 자리에 고정하고 나머지 순서만 바꾼다.
 *
 * <p>최근접 이웃으로 초기 순서를 만든 뒤 2-opt로 구간을 뒤집어 개선한다. 최적해를 보장하지는 않지만 하루 일정 규모(장소 3~10곳)에서는 대개 최적이거나 그에
 * 가깝고, 결과가 입력에만 의존해 같은 입력이면 항상 같은 순서가 나온다.
 */
@Component
@RequiredArgsConstructor
public class VisitOrderOptimizer {

  /** 2-opt 개선 반복 상한. 장소 수가 많아도 계산이 끝나도록 막아둔다. */
  private static final int MAX_IMPROVEMENT_PASSES = 100;

  private final RouteProvider routeProvider;

  /**
   * 방문 순서를 최적화한다.
   *
   * @param start 출발 지점. {@code null}이면 {@code places}의 첫 장소에서 시작한다.
   * @param places 방문할 장소들. 입력 순서가 최적화 전 기준이 된다.
   */
  public VisitOrderResDto optimize(
      RoutePoint start, List<RoutePoint> places, TransportType transportType) {
    TransportType mode = transportType == null ? TransportType.PUBLIC_TRANSPORT : transportType;
    List<RoutePoint> originalOrder = withStart(start, places);

    if (originalOrder.size() < 3) {
      // 지점이 둘 이하면 바꿀 순서가 없다.
      List<RouteLeg> legs = routeProvider.legs(originalOrder, mode);
      int duration = legs.stream().mapToInt(RouteLeg::durationMinutes).sum();
      return VisitOrderResDto.of(mode, originalOrder, legs, duration);
    }

    int[][] durations = routeProvider.durationMatrixMinutes(originalOrder, mode);
    int originalDuration = pathDuration(sequence(originalOrder.size()), durations);

    int[] order = improve(nearestNeighbour(durations), durations);

    List<RoutePoint> optimizedOrder = new ArrayList<>(order.length);
    for (int index : order) {
      optimizedOrder.add(originalOrder.get(index));
    }
    return VisitOrderResDto.of(
        mode, optimizedOrder, routeProvider.legs(optimizedOrder, mode), originalDuration);
  }

  /** 출발 지점을 맨 앞에 두고, 없으면 장소 목록을 그대로 쓴다. */
  private List<RoutePoint> withStart(RoutePoint start, List<RoutePoint> places) {
    List<RoutePoint> points = new ArrayList<>();
    if (start != null) {
      points.add(start);
    }
    if (places != null) {
      points.addAll(places);
    }
    return points;
  }

  /** 0번 지점에서 출발해 매번 가장 가까운 미방문 지점으로 간다. */
  private int[] nearestNeighbour(int[][] durations) {
    int size = durations.length;
    boolean[] visited = new boolean[size];
    int[] order = new int[size];
    order[0] = 0;
    visited[0] = true;

    for (int position = 1; position < size; position++) {
      int current = order[position - 1];
      int nearest = -1;
      for (int candidate = 0; candidate < size; candidate++) {
        if (visited[candidate]) {
          continue;
        }
        if (nearest == -1 || durations[current][candidate] < durations[current][nearest]) {
          nearest = candidate;
        }
      }
      order[position] = nearest;
      visited[nearest] = true;
    }
    return order;
  }

  /**
   * 2-opt: 경로의 한 구간을 통째로 뒤집어 총 시간이 줄면 채택한다.
   *
   * <p>출발 지점은 고정이므로 인덱스 1부터 뒤집는다. 더 줄일 구간이 없을 때까지 반복한다.
   */
  private int[] improve(int[] initialOrder, int[][] durations) {
    int[] order = initialOrder.clone();
    int size = order.length;

    for (int pass = 0; pass < MAX_IMPROVEMENT_PASSES; pass++) {
      boolean improved = false;
      int best = pathDuration(order, durations);

      for (int from = 1; from < size - 1; from++) {
        for (int to = from + 1; to < size; to++) {
          int[] candidate = reversed(order, from, to);
          int duration = pathDuration(candidate, durations);
          if (duration < best) {
            order = candidate;
            best = duration;
            improved = true;
          }
        }
      }

      if (!improved) {
        break;
      }
    }
    return order;
  }

  private int[] reversed(int[] order, int from, int to) {
    int[] candidate = order.clone();
    for (int left = from, right = to; left < right; left++, right--) {
      int swap = candidate[left];
      candidate[left] = candidate[right];
      candidate[right] = swap;
    }
    return candidate;
  }

  private int pathDuration(int[] order, int[][] durations) {
    int total = 0;
    for (int i = 0; i < order.length - 1; i++) {
      total += durations[order[i]][order[i + 1]];
    }
    return total;
  }

  private int[] sequence(int size) {
    int[] order = new int[size];
    for (int i = 0; i < size; i++) {
      order[i] = i;
    }
    return order;
  }
}
