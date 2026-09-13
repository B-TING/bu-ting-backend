package com.butingbe.domain.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.travel.entity.TransportType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HaversineRouteProviderTest {

  private static final RoutePoint BUSAN_STATION = RoutePoint.of("부산역", 35.1151, 129.0413);
  private static final RoutePoint GWANGALLI = RoutePoint.of("광안리", 35.1532, 129.1186);
  private static final RoutePoint HAEUNDAE = RoutePoint.of("해운대", 35.1587, 129.1604);
  private static final RoutePoint SEOMYEON = RoutePoint.of("서면", 35.1580, 129.0596);
  private static final RoutePoint NAMPO = RoutePoint.of("남포동", 35.0979, 129.0301);
  private static final RoutePoint GIJANG = RoutePoint.of("기장", 35.2444, 129.2222);

  private final HaversineRouteProvider provider = new HaversineRouteProvider();

  @Test
  @DisplayName("두 지점의 직선 거리를 대권 거리로 계산한다")
  void calculatesGreatCircleDistance() {
    double meters = provider.straightLineMeters(BUSAN_STATION, GWANGALLI);

    // 부산역 - 광안리는 약 8.3km 떨어져 있다.
    assertThat(meters).isBetween(8_000.0, 8_800.0);
  }

  @Test
  @DisplayName("같은 지점 사이의 거리와 시간은 0이다")
  void samePointHasNoDistance() {
    RouteLeg leg = provider.leg(BUSAN_STATION, BUSAN_STATION, TransportType.WALK);

    assertThat(leg.distanceMeters()).isZero();
    assertThat(leg.durationMinutes()).isZero();
  }

  @Test
  @DisplayName("이동 수단이 빠를수록 같은 구간의 소요 시간이 짧다")
  void fasterTransportTakesLessTime() {
    int walk = provider.leg(BUSAN_STATION, GWANGALLI, TransportType.WALK).durationMinutes();
    int transit =
        provider.leg(BUSAN_STATION, GWANGALLI, TransportType.PUBLIC_TRANSPORT).durationMinutes();
    int car = provider.leg(BUSAN_STATION, GWANGALLI, TransportType.CAR).durationMinutes();

    assertThat(walk).isGreaterThan(transit);
    assertThat(transit).isGreaterThan(car);
  }

  @Test
  @DisplayName("이동 수단을 지정하지 않으면 대중교통으로 계산한다")
  void defaultsToPublicTransport() {
    RouteLeg leg = provider.leg(BUSAN_STATION, GWANGALLI, null);

    assertThat(leg.transportType()).isEqualTo(TransportType.PUBLIC_TRANSPORT);
    assertThat(leg.durationMinutes())
        .isEqualTo(
            provider
                .leg(BUSAN_STATION, GWANGALLI, TransportType.PUBLIC_TRANSPORT)
                .durationMinutes());
  }

  @Test
  @DisplayName("실제 이동 거리는 직선 거리보다 길게 잡는다")
  void appliesDetourFactor() {
    double straight = provider.straightLineMeters(BUSAN_STATION, GWANGALLI);
    RouteLeg leg = provider.leg(BUSAN_STATION, GWANGALLI, TransportType.WALK);

    assertThat(leg.distanceMeters()).isGreaterThan((int) straight);
  }

  @Test
  @DisplayName("순서대로 이동하는 구간 목록을 만든다")
  void buildsLegsInOrder() {
    List<RouteLeg> legs =
        provider.legs(List.of(BUSAN_STATION, GWANGALLI, HAEUNDAE), TransportType.PUBLIC_TRANSPORT);

    assertThat(legs).hasSize(2);
    assertThat(legs.get(0).from().name()).isEqualTo("부산역");
    assertThat(legs.get(0).to().name()).isEqualTo("광안리");
    assertThat(legs.get(1).from().name()).isEqualTo("광안리");
    assertThat(legs.get(1).to().name()).isEqualTo("해운대");
  }

  @Test
  @DisplayName("부산 실측 구간에 대해 대중교통 추정치가 실제와 크게 벌어지지 않는다")
  void transitEstimateStaysCloseToMeasuredBusanRoutes() {
    // Google Routes API(TRANSIT)로 측정한 실제 값. 한국에서는 Google이 대중교통 경로만 제공한다.
    record Measured(RoutePoint from, RoutePoint to, int minutes) {}
    List<Measured> measured =
        List.of(
            new Measured(BUSAN_STATION, GWANGALLI, 48),
            new Measured(BUSAN_STATION, HAEUNDAE, 65),
            new Measured(SEOMYEON, NAMPO, 58),
            new Measured(BUSAN_STATION, SEOMYEON, 37),
            new Measured(HAEUNDAE, GIJANG, 53));

    for (Measured route : measured) {
      int estimated =
          provider.leg(route.from(), route.to(), TransportType.PUBLIC_TRANSPORT).durationMinutes();
      double ratio = (double) estimated / route.minutes();

      assertThat(ratio)
          .as(
              "%s -> %s: 추정 %d분 vs 실측 %d분",
              route.from().name(), route.to().name(), estimated, route.minutes())
          .isBetween(0.6, 1.5);
    }
  }

  @Test
  @DisplayName("지점이 2개 미만이면 구간이 없다")
  void needsAtLeastTwoPoints() {
    assertThat(provider.legs(null, TransportType.WALK)).isEmpty();
    assertThat(provider.legs(List.of(), TransportType.WALK)).isEmpty();
    assertThat(provider.legs(List.of(BUSAN_STATION), TransportType.WALK)).isEmpty();
  }
}
