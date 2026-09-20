package com.butingbe.domain.route;

import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.route.entity.PlaceTravelTime;
import com.butingbe.domain.route.entity.PlaceTravelTimeId;
import com.butingbe.domain.route.repository.PlaceTravelTimeRepository;
import com.butingbe.domain.travel.entity.TransportType;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * 외부 경로 조회 결과를 DB에 캐시한다.
 *
 * <p>일정 생성은 날짜마다 지점 수의 제곱만큼 구간을 조회한다. 같은 관광지 조합이 반복해서 쓰이므로, 한 번 받아온 구간을 다시 외부로 묻지 않는 것만으로 호출량이 크게
 * 준다.
 *
 * <p>키는 장소 식별자가 아니라 반올림한 좌표다. 경로에는 사용자의 현재 위치처럼 카탈로그에 없는 지점도 들어온다.
 *
 * <p>행렬 조회는 한 칸이라도 비면 위임 provider에 통째로 맡긴다. 외부 API가 행렬을 한 번에 받아오는 이점을 버리지 않기 위해서다. 조합이 반복될수록 전부 적중해
 * 호출이 사라진다.
 */
@Slf4j
public class CachingRouteProvider implements RouteProvider {

  private static final int COORDINATE_SCALE = 5;

  private final RouteProvider delegate;
  private final PlaceTravelTimeRepository repository;
  private final Duration ttl;

  public CachingRouteProvider(
      RouteProvider delegate, PlaceTravelTimeRepository repository, Duration ttl) {
    this.delegate = delegate;
    this.repository = repository;
    this.ttl = ttl;
  }

  @Override
  public RouteLeg leg(RoutePoint from, RoutePoint to, TransportType transportType) {
    PlaceTravelTimeId id = idOf(from, to, transportType);
    Optional<PlaceTravelTime> cached = find(id);
    if (cached.isPresent()) {
      PlaceTravelTime hit = cached.get();
      return new RouteLeg(
          from, to, transportType, hit.getDistanceMeters(), hit.getDurationMinutes());
    }

    RouteLeg leg = delegate.leg(from, to, transportType);
    store(id, leg.durationMinutes(), leg.distanceMeters());
    return leg;
  }

  @Override
  public int[][] durationMatrixMinutes(List<RoutePoint> points, TransportType transportType) {
    int size = points.size();
    int[][] matrix = new int[size][size];

    for (int from = 0; from < size; from++) {
      for (int to = 0; to < size; to++) {
        if (from == to) {
          continue;
        }
        Optional<PlaceTravelTime> cached =
            find(idOf(points.get(from), points.get(to), transportType));
        if (cached.isEmpty()) {
          return fetchAndStoreMatrix(points, transportType);
        }
        matrix[from][to] = cached.get().getDurationMinutes();
      }
    }
    return matrix;
  }

  private int[][] fetchAndStoreMatrix(List<RoutePoint> points, TransportType transportType) {
    int[][] matrix = delegate.durationMatrixMinutes(points, transportType);
    for (int from = 0; from < points.size(); from++) {
      for (int to = 0; to < points.size(); to++) {
        if (from == to) {
          continue;
        }
        // 행렬 조회는 거리를 주지 않는다. 거리는 leg 조회에서만 채워진다.
        store(idOf(points.get(from), points.get(to), transportType), matrix[from][to], 0);
      }
    }
    return matrix;
  }

  private Optional<PlaceTravelTime> find(PlaceTravelTimeId id) {
    try {
      return repository.findById(id).filter(entry -> entry.freshAt(LocalDateTime.now().minus(ttl)));
    } catch (RuntimeException e) {
      // 캐시 조회 실패는 미스로 취급한다. 경로 계산이 멈추는 편이 더 나쁘다.
      log.warn("Route cache lookup failed. reason={}", e.toString());
      return Optional.empty();
    }
  }

  private void store(PlaceTravelTimeId id, int durationMinutes, int distanceMeters) {
    try {
      LocalDateTime now = LocalDateTime.now();
      // 갱신도 명시적으로 저장한다. 일정 생성은 트랜잭션 밖에서 경로를 조회하므로,
      // 더티 체킹에 기대면 갱신이 플러시되지 않고 조용히 사라진다.
      PlaceTravelTime entry =
          repository
              .findById(id)
              .orElseGet(() -> new PlaceTravelTime(id, durationMinutes, distanceMeters, now));
      entry.refresh(durationMinutes, distanceMeters, now);
      repository.save(entry);
    } catch (RuntimeException e) {
      // 저장에 실패해도 이번 계산 결과는 이미 손에 있다. 다음 호출에서 다시 저장을 시도한다.
      log.warn("Route cache store failed. reason={}", e.toString());
    }
  }

  private PlaceTravelTimeId idOf(RoutePoint from, RoutePoint to, TransportType transportType) {
    return new PlaceTravelTimeId(key(from), key(to), transportType);
  }

  /** 좌표를 소수점 5자리(약 1m)로 반올림해 키로 쓴다. 같은 장소의 미세한 좌표 차이로 캐시가 갈리지 않게 한다. */
  private String key(RoutePoint point) {
    return String.format(
        Locale.ROOT,
        "%." + COORDINATE_SCALE + "f,%." + COORDINATE_SCALE + "f",
        point.latitude(),
        point.longitude());
  }
}
