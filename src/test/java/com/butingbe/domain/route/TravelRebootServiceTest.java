package com.butingbe.domain.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.route.dto.response.TravelRebootResDto;
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
class TravelRebootServiceTest {

  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");
  private static final UUID TRAVEL_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID PLAN_ID = UUID.fromString("33333333-0000-0000-0000-000000000001");

  @Mock private PlanRepository planRepository;
  @Mock private PlanPlaceRepository planPlaceRepository;
  @Mock private TravelMemberAuthorization travelMemberAuthorization;

  private TravelRebootService travelRebootService;
  private AuthenticatedUser authenticatedUser;
  private Plan plan;

  // 부산 서쪽에서 동쪽으로. 현재 위치는 가장 동쪽(기장).
  private static final RoutePoint CURRENT = RoutePoint.of("현재 위치", 35.2444, 129.2222);

  @BeforeEach
  void setUp() {
    travelRebootService =
        new TravelRebootService(
            planRepository,
            planPlaceRepository,
            travelMemberAuthorization,
            new VisitOrderOptimizer(new HaversineRouteProvider()));
    authenticatedUser = new AuthenticatedUser(USER_ID, "u@example.com", "u", List.of());
    plan = plan();
  }

  @Test
  @DisplayName("방문한 장소는 빼고 미방문 장소만 현재 위치에서 다시 짠다")
  void reschedulesOnlyUnvisitedPlaces() {
    PlanPlace visited = place("해운대", 35.1587, 129.1604, 60, true);
    PlanPlace unvisited1 = place("광안리", 35.1532, 129.1186, 60, false);
    PlanPlace unvisited2 = place("서면", 35.1580, 129.0596, 60, false);
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID))
        .thenReturn(List.of(visited, unvisited1, unvisited2));

    TravelRebootResDto result =
        travelRebootService.reboot(
            authenticatedUser, PLAN_ID, CURRENT, 600, TransportType.PUBLIC_TRANSPORT);

    assertThat(result.visitedPlaceIds()).containsExactly(visited.getId());
    assertThat(result.reachablePlaceIds())
        .containsExactlyInAnyOrder(unvisited1.getId(), unvisited2.getId());
    assertThat(result.orderedPoints().get(0).name()).isEqualTo("현재 위치");
    assertThat(result.droppedForTimePlaceIds()).isEmpty();
    verify(travelMemberAuthorization).validateMember(TRAVEL_ID, USER_ID);
  }

  @Test
  @DisplayName("남은 시간에 담기지 않는 장소는 시간 부족으로 제외한다")
  void dropsPlacesThatDoNotFitInTime() {
    // 체류 60분짜리 장소 3곳. 남은 시간을 아주 짧게 주면 앞쪽 일부만 담긴다.
    PlanPlace a = place("광안리", 35.1532, 129.1186, 60, false);
    PlanPlace b = place("서면", 35.1580, 129.0596, 60, false);
    PlanPlace c = place("남포동", 35.0979, 129.0301, 60, false);
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID)).thenReturn(List.of(a, b, c));

    TravelRebootResDto result =
        travelRebootService.reboot(
            authenticatedUser, PLAN_ID, CURRENT, 200, TransportType.PUBLIC_TRANSPORT);

    assertThat(result.reachablePlaceIds()).hasSize(1);
    assertThat(result.droppedForTimePlaceIds()).hasSize(2);
    assertThat(result.totalMinutes()).isLessThanOrEqualTo(200);
    // 담긴 장소 + 제외 장소 = 미방문 전체
    assertThat(result.reachablePlaceIds().size() + result.droppedForTimePlaceIds().size())
        .isEqualTo(3);
  }

  @Test
  @DisplayName("이동+체류 시간 합이 남은 시간을 넘지 않는다")
  void totalTimeStaysWithinBudget() {
    PlanPlace a = place("광안리", 35.1532, 129.1186, 30, false);
    PlanPlace b = place("서면", 35.1580, 129.0596, 30, false);
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID)).thenReturn(List.of(a, b));

    TravelRebootResDto result =
        travelRebootService.reboot(
            authenticatedUser, PLAN_ID, CURRENT, 500, TransportType.PUBLIC_TRANSPORT);

    assertThat(result.totalMinutes())
        .isEqualTo(result.totalTravelMinutes() + result.totalStayMinutes());
    assertThat(result.totalMinutes()).isLessThanOrEqualTo(500);
    assertThat(result.legs()).hasSize(result.orderedPoints().size() - 1);
  }

  @Test
  @DisplayName("체류 시간이 없는 장소는 이동 시간만으로 계산한다")
  void handlesPlacesWithoutStayTime() {
    PlanPlace a = place("광안리", 35.1532, 129.1186, null, false);
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID)).thenReturn(List.of(a));

    TravelRebootResDto result =
        travelRebootService.reboot(
            authenticatedUser, PLAN_ID, CURRENT, 600, TransportType.PUBLIC_TRANSPORT);

    assertThat(result.totalStayMinutes()).isZero();
    assertThat(result.reachablePlaceIds()).containsExactly(a.getId());
  }

  @Test
  @DisplayName("좌표 없는 미방문 장소는 계산에서 빼고 따로 알린다")
  void reportsPlacesWithoutCoordinates() {
    PlanPlace withoutCoordinates = place("좌표 없음", null, null, 60, false);
    PlanPlace located = place("광안리", 35.1532, 129.1186, 60, false);
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID))
        .thenReturn(List.of(withoutCoordinates, located));

    TravelRebootResDto result =
        travelRebootService.reboot(authenticatedUser, PLAN_ID, CURRENT, 600, null);

    assertThat(result.skippedNoCoordinatesPlaceIds()).containsExactly(withoutCoordinates.getId());
    assertThat(result.reachablePlaceIds()).containsExactly(located.getId());
    assertThat(result.transportType()).isEqualTo(TransportType.PUBLIC_TRANSPORT);
  }

  @Test
  @DisplayName("모든 장소를 다녀왔으면 현재 위치만 남고 갈 곳이 없다")
  void nothingLeftWhenEveryPlaceVisited() {
    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
    when(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(PLAN_ID))
        .thenReturn(
            List.of(
                place("해운대", 35.1587, 129.1604, 60, true),
                place("광안리", 35.1532, 129.1186, 60, true)));

    TravelRebootResDto result =
        travelRebootService.reboot(
            authenticatedUser, PLAN_ID, CURRENT, 600, TransportType.PUBLIC_TRANSPORT);

    assertThat(result.reachablePlaceIds()).isEmpty();
    assertThat(result.orderedPoints()).extracting(RoutePoint::name).containsExactly("현재 위치");
    assertThat(result.legs()).isEmpty();
    assertThat(result.visitedPlaceIds()).hasSize(2);
  }

  @Test
  @DisplayName("리부트도 인증과 일정 존재를 확인한다")
  void checksAuthenticationAndPlan() {
    assertThatThrownBy(() -> travelRebootService.reboot(null, PLAN_ID, CURRENT, 300, null))
        .isInstanceOf(UnauthenticatedException.class);

    when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> travelRebootService.reboot(authenticatedUser, PLAN_ID, CURRENT, 300, null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Plan not found.");
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

  private PlanPlace place(
      String name, Double latitude, Double longitude, Integer stayMinutes, boolean visited) {
    PlanPlace created =
        PlanPlace.builder()
            .plan(plan)
            .sequence(1)
            .placeName(name)
            .address("부산")
            .latitude(latitude)
            .longitude(longitude)
            .provider(PlaceProvider.GOOGLE)
            .providerPlaceId(name)
            .durationMinutes(stayMinutes)
            .visited(visited)
            .build();
    ReflectionTestUtils.setField(created, "id", UUID.randomUUID());
    return created;
  }
}
