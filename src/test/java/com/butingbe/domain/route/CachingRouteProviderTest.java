package com.butingbe.domain.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.route.entity.PlaceTravelTime;
import com.butingbe.domain.route.entity.PlaceTravelTimeId;
import com.butingbe.domain.route.repository.PlaceTravelTimeRepository;
import com.butingbe.domain.travel.entity.TransportType;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CachingRouteProviderTest {

  private static final RoutePoint GAMCHEON = RoutePoint.of("감천문화마을", 35.0975, 129.0107);
  private static final RoutePoint JAGALCHI = RoutePoint.of("자갈치시장", 35.0966, 129.0306);
  private static final TransportType MODE = TransportType.PUBLIC_TRANSPORT;

  @Mock private RouteProvider delegate;
  @Mock private PlaceTravelTimeRepository repository;

  private Map<PlaceTravelTimeId, PlaceTravelTime> store;
  private CachingRouteProvider provider;

  @BeforeEach
  void setUp() {
    store = new HashMap<>();
    lenient()
        .when(repository.findById(any()))
        .thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(0))));
    lenient()
        .when(repository.save(any()))
        .thenAnswer(
            invocation -> {
              PlaceTravelTime entity = invocation.getArgument(0);
              store.put(entity.getId(), entity);
              return entity;
            });
    provider = new CachingRouteProvider(delegate, repository, Duration.ofDays(30));
  }

  @Test
  @DisplayName("같은 구간을 두 번 물으면 외부 호출은 한 번만 나간다")
  void cachesLeg() {
    when(delegate.leg(GAMCHEON, JAGALCHI, MODE)).thenReturn(leg(25, 3200));

    RouteLeg first = provider.leg(GAMCHEON, JAGALCHI, MODE);
    RouteLeg second = provider.leg(GAMCHEON, JAGALCHI, MODE);

    verify(delegate, times(1)).leg(GAMCHEON, JAGALCHI, MODE);
    assertThat(first.durationMinutes()).isEqualTo(25);
    assertThat(second.durationMinutes()).isEqualTo(25);
    assertThat(second.distanceMeters()).isEqualTo(3200);
  }

  @Test
  @DisplayName("좌표가 소수점 5자리 안에서 같으면 같은 구간으로 본다")
  void treatsNearIdenticalCoordinatesAsSameLeg() {
    when(delegate.leg(any(), any(), any())).thenReturn(leg(25, 3200));

    provider.leg(GAMCHEON, JAGALCHI, MODE);
    provider.leg(
        RoutePoint.of("감천문화마을", 35.09750001, 129.01070001),
        RoutePoint.of("자갈치시장", 35.09660001, 129.03060001),
        MODE);

    verify(delegate, times(1)).leg(any(), any(), any());
  }

  @Test
  @DisplayName("교통수단이 다르면 따로 캐시한다")
  void cachesPerTransportType() {
    when(delegate.leg(any(), any(), any())).thenReturn(leg(25, 3200));

    provider.leg(GAMCHEON, JAGALCHI, TransportType.PUBLIC_TRANSPORT);
    provider.leg(GAMCHEON, JAGALCHI, TransportType.WALK);

    verify(delegate, times(2)).leg(any(), any(), any());
  }

  @Test
  @DisplayName("TTL이 지난 항목은 다시 조회한다")
  void refetchesExpiredEntry() {
    CachingRouteProvider shortTtl =
        new CachingRouteProvider(delegate, repository, Duration.ofSeconds(0));
    when(delegate.leg(any(), any(), any())).thenReturn(leg(25, 3200));

    shortTtl.leg(GAMCHEON, JAGALCHI, MODE);
    shortTtl.leg(GAMCHEON, JAGALCHI, MODE);

    verify(delegate, times(2)).leg(any(), any(), any());
  }

  @Test
  @DisplayName("행렬은 한 칸이라도 비면 통째로 위임하고, 전부 적중하면 외부로 나가지 않는다")
  void cachesMatrix() {
    List<RoutePoint> points = List.of(GAMCHEON, JAGALCHI);
    when(delegate.durationMatrixMinutes(points, MODE)).thenReturn(new int[][] {{0, 25}, {24, 0}});

    int[][] first = provider.durationMatrixMinutes(points, MODE);
    int[][] second = provider.durationMatrixMinutes(points, MODE);

    verify(delegate, times(1)).durationMatrixMinutes(points, MODE);
    assertThat(first[0][1]).isEqualTo(25);
    assertThat(second[0][1]).isEqualTo(25);
    assertThat(second[1][0]).isEqualTo(24);
  }

  @Test
  @DisplayName("캐시 조회·저장이 실패해도 경로 계산은 결과를 돌려준다")
  void survivesRepositoryFailure() {
    when(repository.findById(any())).thenThrow(new IllegalStateException("db down"));
    when(delegate.leg(any(), any(), any())).thenReturn(leg(25, 3200));

    RouteLeg result = provider.leg(GAMCHEON, JAGALCHI, MODE);

    assertThat(result.durationMinutes()).isEqualTo(25);
  }

  @Test
  @DisplayName("행렬 조회로 채운 캐시는 거리를 0으로 둔다")
  void matrixCacheHasNoDistance() {
    List<RoutePoint> points = List.of(GAMCHEON, JAGALCHI);
    when(delegate.durationMatrixMinutes(points, MODE)).thenReturn(new int[][] {{0, 25}, {24, 0}});

    provider.durationMatrixMinutes(points, MODE);
    RouteLeg leg = provider.leg(GAMCHEON, JAGALCHI, MODE);

    verify(delegate, never()).leg(any(), any(), any());
    assertThat(leg.durationMinutes()).isEqualTo(25);
    assertThat(leg.distanceMeters()).isZero();
  }

  private RouteLeg leg(int durationMinutes, int distanceMeters) {
    return new RouteLeg(GAMCHEON, JAGALCHI, MODE, distanceMeters, durationMinutes);
  }
}
