package com.butingbe.domain.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.route.dto.response.PlanRouteResDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceSequenceUpdateReqDto;
import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travel.entity.Plan;
import com.butingbe.domain.travel.entity.PlanPlace;
import com.butingbe.domain.travel.entity.TransportType;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.repository.PlanPlaceRepository;
import com.butingbe.domain.travel.repository.PlanRepository;
import com.butingbe.domain.travel.repository.TravelRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TravelRouteServiceTest {

  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");
  private static final UUID TRAVEL_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID PLAN_ID = UUID.fromString("33333333-0000-0000-0000-000000000001");

  @Mock private TravelRepository travelRepository;
  @Mock private PlanRepository planRepository;
  @Mock private PlanPlaceRepository planPlaceRepository;
  @Mock private TravelMemberAuthorization travelMemberAuthorization;
  @Mock private com.butingbe.domain.travel.service.TravelService travelService;

  private TravelRouteService travelRouteService;
  private AuthenticatedUser authenticatedUser;
  private Plan plan;

  @BeforeEach
  void setUp() {
    HaversineRouteProvider routeProvider = new HaversineRouteProvider();
    travelRouteService =
        new TravelRouteService(
            travelRepository,
            planRepository,
            planPlaceRepository,
            travelMemberAuthorization,
            routeProvider,
            new VisitOrderOptimizer(routeProvider),
            travelService);
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

  @Test
  @DisplayName("여행 전체를 일자별로 최적화하고 일자별·전체 합계를 낸다")
  void optimizesEveryDayOfATravel() {
    Plan secondDay = plan(UUID.fromString("33333333-0000-0000-0000-000000000002"), 2);
    when(travelRepository.findById(TRAVEL_ID)).thenReturn(Optional.of(plan.getTravel()));
    when(planRepository.findByTravel_IdOrderByDayNumberAsc(TRAVEL_ID))
        .thenReturn(List.of(plan, secondDay));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID))
        .thenReturn(
            List.of(
                place(1, "해운대", 35.1587, 129.1604),
                place(2, "부산역", 35.1151, 129.0413),
                place(3, "서면", 35.1580, 129.0596)));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(secondDay.getId()))
        .thenReturn(List.of(place(1, "광안리", 35.1532, 129.1186), place(2, "기장", 35.2444, 129.2222)));

    var result =
        travelRouteService.optimizeTravelVisitOrder(
            authenticatedUser, TRAVEL_ID, TransportType.PUBLIC_TRANSPORT);

    assertThat(result.travelId()).isEqualTo(TRAVEL_ID);
    assertThat(result.days()).hasSize(2);
    assertThat(result.days().get(0).dayNumber()).isEqualTo(1);
    assertThat(result.days().get(1).dayNumber()).isEqualTo(2);
    assertThat(result.totalDurationMinutes())
        .isEqualTo(result.days().stream().mapToInt(day -> day.totalDurationMinutes()).sum());
    assertThat(result.savedMinutes())
        .isEqualTo(result.originalDurationMinutes() - result.totalDurationMinutes());
    verify(travelMemberAuthorization).validateMember(TRAVEL_ID, USER_ID);
  }

  @Test
  @DisplayName("일자가 없는 여행도 빈 결과로 응답한다")
  void handlesATravelWithNoDays() {
    when(travelRepository.findById(TRAVEL_ID)).thenReturn(Optional.of(plan.getTravel()));
    when(planRepository.findByTravel_IdOrderByDayNumberAsc(TRAVEL_ID)).thenReturn(List.of());

    var result = travelRouteService.optimizeTravelVisitOrder(authenticatedUser, TRAVEL_ID, null);

    assertThat(result.days()).isEmpty();
    assertThat(result.totalDurationMinutes()).isZero();
    assertThat(result.savedMinutes()).isZero();
    assertThat(result.transportType()).isEqualTo(TransportType.PUBLIC_TRANSPORT);
  }

  @Test
  @DisplayName("존재하지 않는 여행은 최적화할 수 없다")
  void rejectsMissingTravel() {
    when(travelRepository.findById(TRAVEL_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> travelRouteService.optimizeTravelVisitOrder(authenticatedUser, TRAVEL_ID, null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Travel not found.");
  }

  @Test
  @DisplayName("여행 최적화도 인증을 확인한다")
  void travelOptimizationRequiresAuthentication() {
    assertThatThrownBy(() -> travelRouteService.optimizeTravelVisitOrder(null, TRAVEL_ID, null))
        .isInstanceOf(UnauthenticatedException.class);
  }

  @Test
  @DisplayName("좌표 없는 장소는 최적화 결과에서도 빠진 장소로 알려준다")
  void reportsSkippedPlacesInOptimizationResult() {
    PlanPlace withoutCoordinates = place(2, "좌표 없음", null, null);
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID))
        .thenReturn(
            List.of(
                place(1, "부산역", 35.1151, 129.0413),
                withoutCoordinates,
                place(3, "해운대", 35.1587, 129.1604)));

    var result = travelRouteService.optimizeVisitOrder(authenticatedUser, PLAN_ID, null, null);

    assertThat(result.skippedPlaceIds()).containsExactly(withoutCoordinates.getId());
  }

  @Test
  @DisplayName("최적화한 순서를 일정에 반영한다")
  void appliesOptimizedOrder() {
    PlanPlace first = place(1, "해운대", 35.1587, 129.1604);
    PlanPlace second = place(2, "부산역", 35.1151, 129.0413);
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID))
        .thenReturn(List.of(first, second));

    travelRouteService.applyOptimizedOrder(
        authenticatedUser, PLAN_ID, List.of(second.getId(), first.getId()));

    ArgumentCaptor<PlanPlaceSequenceUpdateReqDto> captor =
        ArgumentCaptor.forClass(PlanPlaceSequenceUpdateReqDto.class);
    verify(travelService)
        .updatePlanPlaceSequence(eq(authenticatedUser), eq(PLAN_ID), captor.capture());
    assertThat(captor.getValue().planPlaceIds()).containsExactly(second.getId(), first.getId());
    verify(travelMemberAuthorization).validateMember(TRAVEL_ID, USER_ID);
  }

  @Test
  @DisplayName("요청에서 빠진 장소는 기존 순서를 유지한 채 뒤에 붙어 사라지지 않는다")
  void keepsPlacesMissingFromTheRequest() {
    PlanPlace located = place(1, "부산역", 35.1151, 129.0413);
    PlanPlace withoutCoordinates = place(2, "좌표 없음", null, null);
    PlanPlace anotherLocated = place(3, "해운대", 35.1587, 129.1604);
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID))
        .thenReturn(List.of(located, withoutCoordinates, anotherLocated));

    // 최적화 결과에는 좌표가 있는 두 곳만 담긴다.
    travelRouteService.applyOptimizedOrder(
        authenticatedUser, PLAN_ID, List.of(anotherLocated.getId(), located.getId()));

    ArgumentCaptor<PlanPlaceSequenceUpdateReqDto> captor =
        ArgumentCaptor.forClass(PlanPlaceSequenceUpdateReqDto.class);
    verify(travelService)
        .updatePlanPlaceSequence(eq(authenticatedUser), eq(PLAN_ID), captor.capture());
    assertThat(captor.getValue().planPlaceIds())
        .containsExactly(anotherLocated.getId(), located.getId(), withoutCoordinates.getId());
  }

  @Test
  @DisplayName("이 일정의 것이 아닌 장소나 중복된 장소는 반영하지 않는다")
  void rejectsForeignOrDuplicatedPlaces() {
    PlanPlace onlyPlace = place(1, "부산역", 35.1151, 129.0413);
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID))
        .thenReturn(List.of(onlyPlace));

    assertThatThrownBy(
            () ->
                travelRouteService.applyOptimizedOrder(
                    authenticatedUser, PLAN_ID, List.of(UUID.randomUUID())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Plan place ids do not match this plan.");
    assertThatThrownBy(
            () ->
                travelRouteService.applyOptimizedOrder(
                    authenticatedUser, PLAN_ID, List.of(onlyPlace.getId(), onlyPlace.getId())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Duplicated plan place id exists.");

    verify(travelService, never())
        .updatePlanPlaceSequence(any(), any(), any(PlanPlaceSequenceUpdateReqDto.class));
  }

  @Test
  @DisplayName("반영도 인증과 일정 존재를 확인한다")
  void applyChecksAuthenticationAndPlan() {
    assertThatThrownBy(() -> travelRouteService.applyOptimizedOrder(null, PLAN_ID, List.of()))
        .isInstanceOf(UnauthenticatedException.class);

    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> travelRouteService.applyOptimizedOrder(authenticatedUser, PLAN_ID, List.of()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("제외한 장소를 빼고 남은 장소로 대체 경로를 만든다")
  void generatesAlternativeRouteWithoutExcludedPlaces() {
    PlanPlace nampo = place(1, "남포동", 35.0979, 129.0301);
    PlanPlace busanStation = place(2, "부산역", 35.1151, 129.0413);
    PlanPlace gwangalli = place(3, "광안리", 35.1532, 129.1186);
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID))
        .thenReturn(List.of(nampo, busanStation, gwangalli));

    var result =
        travelRouteService.generateAlternativeRoute(
            authenticatedUser,
            PLAN_ID,
            List.of(busanStation.getId()),
            null,
            TransportType.PUBLIC_TRANSPORT);

    assertThat(result.excludedPlaceIds()).containsExactly(busanStation.getId());
    assertThat(result.alternative().orderedPoints())
        .extracting(RoutePoint::name)
        .containsExactlyInAnyOrder("남포동", "광안리")
        .doesNotContain("부산역");
    assertThat(result.originalDurationMinutes()).isPositive();
    assertThat(result.reducedMinutes())
        .isEqualTo(
            Math.max(0, result.originalDurationMinutes() - result.alternativeDurationMinutes()));
  }

  @Test
  @DisplayName("장소를 뺐으니 대체 경로 이동 시간이 기존 경로보다 짧다")
  void alternativeRouteIsShorterAfterRemovingAPlace() {
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID))
        .thenReturn(
            List.of(
                place(1, "남포동", 35.0979, 129.0301),
                place(2, "부산역", 35.1151, 129.0413),
                place(3, "광안리", 35.1532, 129.1186),
                place(4, "기장", 35.2444, 129.2222)));
    UUID gijangId = planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID).get(3).getId();

    var result =
        travelRouteService.generateAlternativeRoute(
            authenticatedUser, PLAN_ID, List.of(gijangId), null, TransportType.PUBLIC_TRANSPORT);

    assertThat(result.alternativeDurationMinutes()).isLessThan(result.originalDurationMinutes());
    assertThat(result.reducedMinutes()).isPositive();
  }

  @Test
  @DisplayName("출발 좌표를 주면 그 지점에서 시작하는 대체 경로를 만든다")
  void generatesAlternativeRouteFromAGivenStart() {
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID))
        .thenReturn(
            List.of(place(1, "남포동", 35.0979, 129.0301), place(2, "광안리", 35.1532, 129.1186)));

    var result =
        travelRouteService.generateAlternativeRoute(
            authenticatedUser,
            PLAN_ID,
            List.of(),
            RoutePoint.of("현재 위치", 35.2444, 129.2222),
            TransportType.PUBLIC_TRANSPORT);

    assertThat(result.alternative().orderedPoints().get(0).name()).isEqualTo("현재 위치");
    assertThat(result.excludedPlaceIds()).isEmpty();
  }

  @Test
  @DisplayName("좌표 없는 장소는 대체 경로에서도 빠진 장소로 알려준다")
  void alternativeRouteReportsSkippedPlaces() {
    PlanPlace withoutCoordinates = place(2, "좌표 없음", null, null);
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID))
        .thenReturn(
            List.of(
                place(1, "남포동", 35.0979, 129.0301),
                withoutCoordinates,
                place(3, "광안리", 35.1532, 129.1186)));

    var result =
        travelRouteService.generateAlternativeRoute(
            authenticatedUser, PLAN_ID, List.of(), null, null);

    assertThat(result.skippedPlaceIds()).containsExactly(withoutCoordinates.getId());
    assertThat(result.transportType()).isEqualTo(TransportType.PUBLIC_TRANSPORT);
  }

  @Test
  @DisplayName("대체 경로도 인증과 일정 존재를 확인한다")
  void alternativeRouteChecksAuthenticationAndPlan() {
    assertThatThrownBy(
            () -> travelRouteService.generateAlternativeRoute(null, PLAN_ID, List.of(), null, null))
        .isInstanceOf(UnauthenticatedException.class);

    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                travelRouteService.generateAlternativeRoute(
                    authenticatedUser, PLAN_ID, List.of(), null, null))
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

  private Plan plan(UUID planId, int dayNumber) {
    Plan created =
        Plan.builder()
            .travel(plan.getTravel())
            .dayNumber(dayNumber)
            .visitDate(LocalDate.of(2026, 9, dayNumber))
            .build();
    ReflectionTestUtils.setField(created, "id", planId);
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
