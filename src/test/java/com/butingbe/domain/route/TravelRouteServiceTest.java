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
    travelRouteService =
        new TravelRouteService(
            planRepository,
            planPlaceRepository,
            travelMemberAuthorization,
            new HaversineRouteProvider());
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
