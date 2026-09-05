package com.butingbe.domain.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.travel.entity.TransportType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResilientRouteProviderTest {

  private static final RoutePoint FROM = RoutePoint.of("부산역", 35.1151, 129.0413);
  private static final RoutePoint TO = RoutePoint.of("광안리", 35.1532, 129.1186);

  private final HaversineRouteProvider fallback = new HaversineRouteProvider();

  @Test
  @DisplayName("주 provider가 성공하면 그 결과를 그대로 쓴다")
  void usesPrimaryWhenItSucceeds() {
    RouteProvider primary = mock(RouteProvider.class);
    RouteLeg primaryLeg = new RouteLeg(FROM, TO, TransportType.PUBLIC_TRANSPORT, 9_805, 48);
    when(primary.leg(FROM, TO, TransportType.PUBLIC_TRANSPORT)).thenReturn(primaryLeg);

    RouteLeg result =
        new ResilientRouteProvider(primary, fallback).leg(FROM, TO, TransportType.PUBLIC_TRANSPORT);

    assertThat(result).isSameAs(primaryLeg);
  }

  @Test
  @DisplayName("주 provider가 실패하면 폴백으로 같은 구간을 계산한다")
  void fallsBackWhenPrimaryFails() {
    RouteProvider primary = mock(RouteProvider.class);
    when(primary.leg(any(), any(), any()))
        .thenThrow(new IllegalStateException("Google Routes returned no route."));

    RouteLeg result =
        new ResilientRouteProvider(primary, fallback).leg(FROM, TO, TransportType.PUBLIC_TRANSPORT);

    // 폴백(좌표 계산)이 실제 값을 채운다.
    assertThat(result.distanceMeters()).isPositive();
    assertThat(result.durationMinutes()).isPositive();
    assertThat(result.transportType()).isEqualTo(TransportType.PUBLIC_TRANSPORT);
  }

  @Test
  @DisplayName("타임아웃 같은 런타임 예외도 폴백으로 흡수한다")
  void absorbsTimeoutAsFallback() {
    RouteProvider primary = mock(RouteProvider.class);
    when(primary.leg(any(), any(), any()))
        .thenThrow(new org.springframework.web.client.ResourceAccessException("Read timed out"));

    RouteLeg result =
        new ResilientRouteProvider(primary, fallback).leg(FROM, TO, TransportType.CAR);

    assertThat(result.distanceMeters()).isPositive();
  }

  @Test
  @DisplayName("구간마다 폴백하므로 일부 구간만 실패해도 나머지는 주 provider 결과를 쓴다")
  void fallsBackPerLeg() {
    RoutePoint third = RoutePoint.of("해운대", 35.1587, 129.1604);
    RouteProvider primary = mock(RouteProvider.class);
    RouteLeg okLeg = new RouteLeg(FROM, TO, TransportType.PUBLIC_TRANSPORT, 9_805, 48);
    when(primary.leg(FROM, TO, TransportType.PUBLIC_TRANSPORT)).thenReturn(okLeg);
    when(primary.leg(TO, third, TransportType.PUBLIC_TRANSPORT))
        .thenThrow(new IllegalStateException("no route"));

    List<RouteLeg> legs =
        new ResilientRouteProvider(primary, fallback)
            .legs(List.of(FROM, TO, third), TransportType.PUBLIC_TRANSPORT);

    assertThat(legs).hasSize(2);
    assertThat(legs.get(0)).isSameAs(okLeg);
    assertThat(legs.get(1).distanceMeters()).isPositive();
  }

  @Test
  @DisplayName("소요 시간 행렬도 주 provider 실패 시 폴백으로 계산한다")
  void fallsBackForDurationMatrix() {
    RouteProvider primary = mock(RouteProvider.class);
    when(primary.durationMatrixMinutes(any(), any()))
        .thenThrow(new IllegalStateException("rate limited"));

    int[][] matrix =
        new ResilientRouteProvider(primary, fallback)
            .durationMatrixMinutes(List.of(FROM, TO), TransportType.PUBLIC_TRANSPORT);

    assertThat(matrix).hasDimensions(2, 2);
    assertThat(matrix[0][0]).isZero();
    assertThat(matrix[0][1]).isPositive();
    verify(primary).durationMatrixMinutes(any(), any());
  }

  @Test
  @DisplayName("주 provider의 행렬 조회가 성공하면 그 값을 쓴다")
  void usesPrimaryMatrixWhenItSucceeds() {
    RouteProvider primary = mock(RouteProvider.class);
    int[][] primaryMatrix = {{0, 30}, {30, 0}};
    when(primary.durationMatrixMinutes(any(), any())).thenReturn(primaryMatrix);

    int[][] matrix =
        new ResilientRouteProvider(primary, fallback)
            .durationMatrixMinutes(List.of(FROM, TO), TransportType.PUBLIC_TRANSPORT);

    assertThat(matrix).isSameAs(primaryMatrix);
  }
}
