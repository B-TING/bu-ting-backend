package com.butingbe.domain.route;

import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.travel.entity.TransportType;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * 주 provider가 실패하면 폴백 provider로 넘어가 경로 계산이 끊기지 않게 한다.
 *
 * <p>외부 경로 API의 타임아웃·조회 실패·Rate Limit이 여행 서비스 전체 장애로 번지지 않도록 흡수한다. 주 provider가 던진 예외는 여기서 삼키고 폴백으로
 * 같은 계산을 다시 해, 호출자는 언제나 결과를 받는다.
 *
 * <p>구간(leg) 단위로 폴백하므로 일부 구간만 외부 조회에 실패해도 그 구간만 폴백값으로 채운다.
 */
@Slf4j
public class ResilientRouteProvider implements RouteProvider {

  private final RouteProvider primary;
  private final RouteProvider fallback;

  public ResilientRouteProvider(RouteProvider primary, RouteProvider fallback) {
    this.primary = primary;
    this.fallback = fallback;
  }

  @Override
  public RouteLeg leg(RoutePoint from, RoutePoint to, TransportType transportType) {
    try {
      return primary.leg(from, to, transportType);
    } catch (RuntimeException e) {
      log.warn(
          "Primary route provider failed for {} -> {}; falling back. reason={}",
          from.name(),
          to.name(),
          e.toString());
      return fallback.leg(from, to, transportType);
    }
  }

  @Override
  public int[][] durationMatrixMinutes(List<RoutePoint> points, TransportType transportType) {
    try {
      return primary.durationMatrixMinutes(points, transportType);
    } catch (RuntimeException e) {
      log.warn(
          "Primary route provider failed for duration matrix; falling back. reason={}",
          e.toString());
      return fallback.durationMatrixMinutes(points, transportType);
    }
  }
}
