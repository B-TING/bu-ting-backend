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

  /**
   * 직선 거리 대비 실제 이동 거리 비율.
   *
   * <p>대중교통 값은 부산 구간 6건(부산역-광안리, 부산역-해운대, 서면-남포동, 광안리-감천문화마을, 부산역-서면, 해운대-기장)의 Google Routes 실측에서
   * 얻은 중앙값 1.32다. 도보와 차량은 한국에서 Google이 경로를 제공하지 않아 실측이 없고, 대중교통 값을 기준으로 잡은 추정치다.
   */
  private double detourFactor(TransportType transportType) {
    return switch (transportType) {
      case WALK -> 1.25;
      case PUBLIC_TRANSPORT -> 1.32;
      case CAR -> 1.35;
    };
  }

  /**
   * 출발지에서 목적지까지의 실효 속도(km/h).
   *
   * <p>대중교통은 위 실측 6건의 중앙값 12.2km/h다. 차량 주행 속도가 아니라 대기·환승·도보 접근을 모두 포함한 문전 속도이므로 순수 주행 속도보다 훨씬 낮다.
   * 초기에 20km/h로 잡았을 때 실제보다 27% 짧게 나왔다.
   */
  private double averageSpeedKmPerHour(TransportType transportType) {
    return switch (transportType) {
      case WALK -> 4.5;
      case PUBLIC_TRANSPORT -> 12.2;
      case CAR -> 25.0;
    };
  }
}
