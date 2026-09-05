package com.butingbe.domain.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.route.dto.response.VisitOrderResDto;
import com.butingbe.domain.travel.entity.TransportType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VisitOrderOptimizerTest {

  // 부산 서쪽에서 동쪽으로 늘어선 지점들. 지리적으로 이 순서가 최단이다.
  private static final RoutePoint NAMPO = RoutePoint.of("남포동", 35.0979, 129.0301);
  private static final RoutePoint BUSAN_STATION = RoutePoint.of("부산역", 35.1151, 129.0413);
  private static final RoutePoint SEOMYEON = RoutePoint.of("서면", 35.1580, 129.0596);
  private static final RoutePoint GWANGALLI = RoutePoint.of("광안리", 35.1532, 129.1186);
  private static final RoutePoint HAEUNDAE = RoutePoint.of("해운대", 35.1587, 129.1604);
  private static final RoutePoint GIJANG = RoutePoint.of("기장", 35.2444, 129.2222);

  private final VisitOrderOptimizer optimizer =
      new VisitOrderOptimizer(new HaversineRouteProvider());

  @Test
  @DisplayName("뒤섞인 순서를 지리적으로 이어지는 순서로 다시 배열한다")
  void reordersScatteredPlacesIntoAContinuousPath() {
    VisitOrderResDto result =
        optimizer.optimize(
            NAMPO,
            List.of(HAEUNDAE, BUSAN_STATION, GIJANG, SEOMYEON, GWANGALLI),
            TransportType.PUBLIC_TRANSPORT);

    assertThat(result.orderedPoints())
        .extracting(RoutePoint::name)
        .containsExactly("남포동", "부산역", "서면", "광안리", "해운대", "기장");
  }

  @Test
  @DisplayName("최적화하면 원래 순서보다 이동 시간이 줄고 줄어든 만큼을 알려준다")
  void reportsHowMuchTimeWasSaved() {
    VisitOrderResDto result =
        optimizer.optimize(
            NAMPO,
            List.of(GIJANG, BUSAN_STATION, HAEUNDAE, SEOMYEON, GWANGALLI),
            TransportType.PUBLIC_TRANSPORT);

    assertThat(result.totalDurationMinutes()).isLessThan(result.originalDurationMinutes());
    assertThat(result.savedMinutes())
        .isEqualTo(result.originalDurationMinutes() - result.totalDurationMinutes());
    assertThat(result.savedMinutes()).isPositive();
  }

  @Test
  @DisplayName("이미 최적인 순서는 그대로 두고 절약 시간을 0으로 보고한다")
  void keepsAnAlreadyOptimalOrder() {
    List<RoutePoint> alreadyOptimal = List.of(BUSAN_STATION, SEOMYEON, GWANGALLI, HAEUNDAE);

    VisitOrderResDto result =
        optimizer.optimize(NAMPO, alreadyOptimal, TransportType.PUBLIC_TRANSPORT);

    assertThat(result.orderedPoints())
        .extracting(RoutePoint::name)
        .containsExactly("남포동", "부산역", "서면", "광안리", "해운대");
    assertThat(result.savedMinutes()).isZero();
  }

  @Test
  @DisplayName("출발 지점은 순서를 바꾸지 않고 항상 맨 앞에 둔다")
  void keepsTheStartingPointFirst() {
    VisitOrderResDto result =
        optimizer.optimize(
            GIJANG, List.of(NAMPO, BUSAN_STATION, SEOMYEON), TransportType.PUBLIC_TRANSPORT);

    // 기장은 동쪽 끝이라 지리적으로는 중간이 나은데도 출발지이므로 맨 앞을 지킨다.
    assertThat(result.orderedPoints().get(0).name()).isEqualTo("기장");
  }

  @Test
  @DisplayName("출발 지점을 주지 않으면 첫 장소에서 시작한다")
  void startsFromTheFirstPlaceWhenNoStartGiven() {
    VisitOrderResDto result =
        optimizer.optimize(
            null, List.of(SEOMYEON, HAEUNDAE, NAMPO, GWANGALLI), TransportType.PUBLIC_TRANSPORT);

    assertThat(result.orderedPoints().get(0).name()).isEqualTo("서면");
    assertThat(result.orderedPoints()).hasSize(4);
  }

  @Test
  @DisplayName("최근접 이웃이 잘못 고른 순서를 구간을 뒤집어 바로잡는다")
  void fixesAGreedyMistakeByReversingASegment() {
    // 출발지 남쪽에 가까운 지점, 북쪽에 가까운 지점, 그보다 훨씬 북쪽인 지점.
    // 가장 가까운 곳부터 고르면 북 -> 남 -> 최북단으로 되돌아가 크게 돌아간다.
    RoutePoint start = RoutePoint.of("출발", 35.00, 129.00);
    RoutePoint north = RoutePoint.of("북쪽", 35.05, 129.00);
    RoutePoint south = RoutePoint.of("남쪽", 34.95, 129.00);
    RoutePoint farNorth = RoutePoint.of("최북단", 35.15, 129.00);

    VisitOrderResDto result =
        optimizer.optimize(start, List.of(north, south, farNorth), TransportType.PUBLIC_TRANSPORT);

    // 남쪽을 먼저 들르고 북쪽으로 한 방향으로 올라가는 것이 짧다.
    assertThat(result.orderedPoints())
        .extracting(RoutePoint::name)
        .containsExactly("출발", "남쪽", "북쪽", "최북단");
    assertThat(result.savedMinutes()).isPositive();
  }

  @Test
  @DisplayName("장소가 둘 이하면 바꿀 순서가 없다")
  void nothingToReorderWithTwoOrFewerPoints() {
    VisitOrderResDto empty = optimizer.optimize(null, List.of(), TransportType.WALK);
    VisitOrderResDto single = optimizer.optimize(null, List.of(NAMPO), TransportType.WALK);
    VisitOrderResDto pair = optimizer.optimize(NAMPO, List.of(HAEUNDAE), TransportType.WALK);

    assertThat(empty.orderedPoints()).isEmpty();
    assertThat(empty.legs()).isEmpty();
    assertThat(single.orderedPoints()).hasSize(1);
    assertThat(single.legs()).isEmpty();
    assertThat(pair.orderedPoints()).extracting(RoutePoint::name).containsExactly("남포동", "해운대");
    assertThat(pair.legs()).hasSize(1);
    assertThat(pair.savedMinutes()).isZero();
  }

  @Test
  @DisplayName("입력한 장소를 빠짐없이 한 번씩만 포함한다")
  void keepsEveryPlaceExactlyOnce() {
    List<RoutePoint> places = List.of(HAEUNDAE, BUSAN_STATION, GIJANG, SEOMYEON, GWANGALLI);

    VisitOrderResDto result = optimizer.optimize(NAMPO, places, TransportType.CAR);

    assertThat(result.orderedPoints()).hasSize(places.size() + 1);
    assertThat(result.orderedPoints()).doesNotHaveDuplicates();
    assertThat(result.orderedPoints()).contains(NAMPO).containsAll(places);
  }

  @Test
  @DisplayName("같은 입력이면 항상 같은 순서가 나온다")
  void isDeterministic() {
    List<RoutePoint> places = List.of(HAEUNDAE, BUSAN_STATION, GIJANG, SEOMYEON, GWANGALLI);

    List<String> first =
        optimizer.optimize(NAMPO, places, TransportType.WALK).orderedPoints().stream()
            .map(RoutePoint::name)
            .toList();
    List<String> second =
        optimizer.optimize(NAMPO, places, TransportType.WALK).orderedPoints().stream()
            .map(RoutePoint::name)
            .toList();

    assertThat(first).isEqualTo(second);
  }

  @Test
  @DisplayName("이동 수단을 지정하지 않으면 대중교통으로 계산한다")
  void defaultsToPublicTransport() {
    VisitOrderResDto result = optimizer.optimize(NAMPO, List.of(SEOMYEON, HAEUNDAE), null);

    assertThat(result.transportType()).isEqualTo(TransportType.PUBLIC_TRANSPORT);
  }

  @Test
  @DisplayName("구간 거리와 시간의 합이 총계와 일치한다")
  void totalsMatchTheLegs() {
    VisitOrderResDto result =
        optimizer.optimize(
            NAMPO, List.of(HAEUNDAE, SEOMYEON, GWANGALLI), TransportType.PUBLIC_TRANSPORT);

    assertThat(result.totalDistanceMeters())
        .isEqualTo(result.legs().stream().mapToInt(leg -> leg.distanceMeters()).sum());
    assertThat(result.totalDurationMinutes())
        .isEqualTo(result.legs().stream().mapToInt(leg -> leg.durationMinutes()).sum());
    assertThat(result.legs()).hasSize(result.orderedPoints().size() - 1);
  }
}
