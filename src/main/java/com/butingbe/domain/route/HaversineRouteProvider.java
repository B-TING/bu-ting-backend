package com.butingbe.domain.route;

import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.travel.entity.TransportType;
import org.springframework.stereotype.Component;

/**
 * 좌표만으로 거리와 소요 시간을 근사하는 기본 구현.
 *
 * <p>외부 경로 API 없이도 순서 최적화와 대체 경로를 계산할 수 있게 한다. 직선 거리에 이동 수단별 우회 계수를 곱해 실제 이동 거리를 추정하고, 평균 속도로 나눠 소요
 * 시간을 낸다. 실제 도로망을 반영하지 않으므로 정확한 안내가 아니라 순서를 비교하기 위한 기준값이다.
 */
@Component
public class HaversineRouteProvider implements RouteProvider {

  private static final double EARTH_RADIUS_METERS = 6_371_000;

  @Override
  public RouteLeg leg(RoutePoint from, RoutePoint to, TransportType transportType) {
    TransportType mode = transportType == null ? TransportType.PUBLIC_TRANSPORT : transportType;
    int distanceMeters = (int) Math.round(straightLineMeters(from, to) * detourFactor(mode));
    int durationMinutes =
        (int) Math.ceil(distanceMeters / 1000.0 / averageSpeedKmPerHour(mode) * 60);
    return new RouteLeg(from, to, mode, distanceMeters, durationMinutes);
  }

  /** 두 지점 사이의 대권 거리(미터). */
  public double straightLineMeters(RoutePoint from, RoutePoint to) {
    double latitudeDelta = Math.toRadians(to.latitude() - from.latitude());
    double longitudeDelta = Math.toRadians(to.longitude() - from.longitude());
    double haversine =
        Math.pow(Math.sin(latitudeDelta / 2), 2)
            + Math.cos(Math.toRadians(from.latitude()))
                * Math.cos(Math.toRadians(to.latitude()))
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
    return EARTH_RADIUS_METERS * 2 * Math.asin(Math.sqrt(Math.min(1, haversine)));
  }

  /** 직선 거리 대비 실제 이동 거리 비율. 도보는 골목을 돌아가고 차량은 도로를 따라간다. */
  private double detourFactor(TransportType transportType) {
    return switch (transportType) {
      case WALK -> 1.3;
      case PUBLIC_TRANSPORT -> 1.4;
      case CAR -> 1.35;
    };
  }

  /** 정차와 신호를 포함한 도심 평균 속도(km/h). */
  private double averageSpeedKmPerHour(TransportType transportType) {
    return switch (transportType) {
      case WALK -> 4.5;
      case PUBLIC_TRANSPORT -> 20.0;
      case CAR -> 30.0;
    };
  }
}
