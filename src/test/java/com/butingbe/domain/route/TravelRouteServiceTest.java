package com.butingbe.domain.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.route.dto.response.PlanRouteResDto;
import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travel.entity.Plan;
import com.butingbe.domain.travel.entity.PlanPlace;
import com.butingbe.domain.travel.entity.TransportType;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.repository.PlanPlaceRepository;
import com.butingbe.domain.travel.repository.PlanRepository;
import com.butingbe.domain.travelteam.service.TravelMemberAuthorization;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TravelRouteServiceTest {

  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");
  private static final UUID TRAVEL_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID PLAN_ID = UUID.fromString("33333333-0000-0000-0000-000000000001");

  @Mock private PlanRepository planRepository;
  @Mock private PlanPlaceRepository planPlaceRepository;
  @Mock private TravelMemberAuthorization travelMemberAuthorization;

  private TravelRouteService travelRouteService;
  private AuthenticatedUser authenticatedUser;
  private Plan plan;

  @BeforeEach
  void setUp() {
    HaversineRouteProvider routeProvider = new HaversineRouteProvider();
    travelRouteService =
        new TravelRouteService(
            planRepository,
            planPlaceRepository,
            travelMemberAuthorization,
            routeProvider,
            new VisitOrderOptimizer(routeProvider));
    authenticatedUser = new AuthenticatedUser(USER_ID, "u@example.com", "u", List.of());
    plan = plan();
  }

  @Test
  @DisplayName("일정 순서대로 구간을 만들고 거리·시간 합계를 낸다")
  void buildsRouteInPlanOrder() {
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID))
        .thenReturn(
            List.of(
                place(1, "부산역", 35.1151, 129.0413),
                place(2, "광안리", 35.1532, 129.1186),
                place(3, "해운대", 35.1587, 129.1604)));

    PlanRouteResDto route =
        travelRouteService.getPlanRoute(authenticatedUser, PLAN_ID, TransportType.PUBLIC_TRANSPORT);

    assertThat(route.planId()).isEqualTo(PLAN_ID);
    assertThat(route.legs()).hasSize(2);
    assertThat(route.totalDistanceMeters())
        .isEqualTo(route.legs().stream().mapToInt(l -> l.distanceMeters()).sum());
    assertThat(route.totalDurationMinutes())
        .isEqualTo(route.legs().stream().mapToInt(l -> l.durationMinutes()).sum());
    assertThat(route.skippedPlaceIds()).isEmpty();
    verify(travelMemberAuthorization).validateMember(TRAVEL_ID, USER_ID);
  }

  @Test
  @DisplayName("좌표가 없는 장소는 계산에서 빼고 어떤 장소였는지 알려준다")
  void skipsPlacesWithoutCoordinates() {
    PlanPlace withoutCoordinates = place(2, "좌표 없음", null, null);
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID))
        .thenReturn(
            List.of(
                place(1, "부산역", 35.1151, 129.0413),
                withoutCoordinates,
                place(3, "해운대", 35.1587, 129.1604)));

    PlanRouteResDto route =
        travelRouteService.getPlanRoute(authenticatedUser, PLAN_ID, TransportType.WALK);

    assertThat(route.legs()).hasSize(1);
    assertThat(route.legs().get(0).from().name()).isEqualTo("부산역");
    assertThat(route.legs().get(0).to().name()).isEqualTo("해운대");
    assertThat(route.skippedPlaceIds()).containsExactly(withoutCoordinates.getId());
  }

  @Test
  @DisplayName("장소가 하나뿐이면 이동 구간이 없다")
  void singlePlaceHasNoLegs() {
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID))
        .thenReturn(List.of(place(1, "부산역", 35.1151, 129.0413)));

    PlanRouteResDto route = travelRouteService.getPlanRoute(authenticatedUser, PLAN_ID, null);

    assertThat(route.legs()).isEmpty();
    assertThat(route.totalDistanceMeters()).isZero();
    assertThat(route.totalDurationMinutes()).isZero();
    assertThat(route.transportType()).isEqualTo(TransportType.PUBLIC_TRANSPORT);
  }

  @Test
  @DisplayName("존재하지 않는 일정은 조회할 수 없다")
  void rejectsMissingPlan() {
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> travelRouteService.getPlanRoute(authenticatedUser, PLAN_ID, TransportType.CAR))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Plan not found.");
  }

  @Test
  @DisplayName("인증 정보가 없거나 id가 없으면 조회할 수 없다")
  void rejectsUnauthenticatedUser() {
    AuthenticatedUser withoutId = new AuthenticatedUser(null, "u@example.com", "u", List.of());

    assertThatThrownBy(() -> travelRouteService.getPlanRoute(null, PLAN_ID, null))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> travelRouteService.getPlanRoute(withoutId, PLAN_ID, null))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(
            () ->
                travelRouteService.getLeg(
                    null, RoutePoint.of("a", 35.0, 129.0), RoutePoint.of("b", 35.1, 129.1), null))
        .isInstanceOf(UnauthenticatedException.class);
  }

  @Test
  @DisplayName("일정 밖의 두 지점도 구간을 계산할 수 있다")
  void calculatesLegOutsideAPlan() {
    var leg =
        travelRouteService.getLeg(
            authenticatedUser,
            RoutePoint.of("현재 위치", 35.1151, 129.0413),
            RoutePoint.of("광안리", 35.1532, 129.1186),
            TransportType.CAR);

    assertThat(leg.transportType()).isEqualTo(TransportType.CAR);
    assertThat(leg.distanceMeters()).isPositive();
    assertThat(leg.durationMinutes()).isPositive();
  }

  @Test
  @DisplayName("일정의 방문 순서를 최적화해 제안한다")
  void optimizesVisitOrder() {
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID))
        .thenReturn(
            List.of(
                place(1, "해운대", 35.1587, 129.1604),
                place(2, "부산역", 35.1151, 129.0413),
                place(3, "서면", 35.1580, 129.0596)));

    var result =
        travelRouteService.optimizeVisitOrder(
            authenticatedUser, PLAN_ID, null, TransportType.PUBLIC_TRANSPORT);

    assertThat(result.orderedPoints()).hasSize(3);
    assertThat(result.totalDurationMinutes()).isLessThanOrEqualTo(result.originalDurationMinutes());
    verify(travelMemberAuthorization).validateMember(TRAVEL_ID, USER_ID);
  }

  @Test
  @DisplayName("출발 좌표를 주면 그 지점에서 시작하는 순서를 계산한다")
  void optimizesFromAGivenStartingPoint() {
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID))
        .thenReturn(
            List.of(place(1, "부산역", 35.1151, 129.0413), place(2, "해운대", 35.1587, 129.1604)));

    var result =
        travelRouteService.optimizeVisitOrder(
            authenticatedUser,
            PLAN_ID,
            RoutePoint.of("현재 위치", 35.2444, 129.2222),
            TransportType.PUBLIC_TRANSPORT);

    assertThat(result.orderedPoints().get(0).name()).isEqualTo("현재 위치");
    assertThat(result.orderedPoints()).hasSize(3);
  }

  @Test
  @DisplayName("좌표 없는 장소는 최적화에서도 빠진다")
  void optimizationSkipsPlacesWithoutCoordinates() {
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID))
        .thenReturn(
            List.of(
                place(1, "부산역", 35.1151, 129.0413),
                place(2, "좌표 없음", null, null),
                place(3, "해운대", 35.1587, 129.1604)));

    var result = travelRouteService.optimizeVisitOrder(authenticatedUser, PLAN_ID, null, null);

    assertThat(result.orderedPoints()).extracting(RoutePoint::name).containsExactly("부산역", "해운대");
  }

  @Test
  @DisplayName("최적화도 인증과 일정 존재를 확인한다")
  void optimizationChecksAuthenticationAndPlan() {
    assertThatThrownBy(() -> travelRouteService.optimizeVisitOrder(null, PLAN_ID, null, null))
        .isInstanceOf(UnauthenticatedException.class);

    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> travelRouteService.optimizeVisitOrder(authenticatedUser, PLAN_ID, null, null))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  private Plan plan() {
    Travel travel =
        Travel.builder()
            .title("부산")
            .startDate(LocalDate.of(2026, 9, 1))
            .endDate(LocalDate.of(2026, 9, 3))
            .build();
    ReflectionTestUtils.setField(travel, "id", TRAVEL_ID);
    Plan created =
        Plan.builder().travel(travel).dayNumber(1).visitDate(LocalDate.of(2026, 9, 1)).build();
    ReflectionTestUtils.setField(created, "id", PLAN_ID);
    return created;
  }

  private PlanPlace place(int sequence, String name, Double latitude, Double longitude) {
    PlanPlace created =
        PlanPlace.builder()
            .plan(plan)
            .sequence(sequence)
            .placeName(name)
            .address("부산")
            .latitude(latitude)
            .longitude(longitude)
            .provider(PlaceProvider.GOOGLE)
            .providerPlaceId(name)
            .build();
    ReflectionTestUtils.setField(created, "id", UUID.randomUUID());
    return created;
  }
}
