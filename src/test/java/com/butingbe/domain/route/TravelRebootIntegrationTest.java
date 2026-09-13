package com.butingbe.domain.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.route.dto.response.TravelRebootResDto;
import com.butingbe.domain.travel.dto.request.PlanCreateReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceCreateReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceVisitedUpdateReqDto;
import com.butingbe.domain.travel.dto.request.TravelCreateReqDto;
import com.butingbe.domain.travel.dto.response.PlanPlaceResDto;
import com.butingbe.domain.travel.dto.response.PlanResDto;
import com.butingbe.domain.travel.dto.response.TravelResDto;
import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travel.entity.TransportType;
import com.butingbe.domain.travel.service.TravelService;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.support.AbstractContainerTest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리부트 핵심 흐름을 실제 DB(Testcontainers)로 검증한다(#137 완료 조건).
 *
 * <p>여행·일정·장소를 실제로 저장하고, 일부를 방문 처리한 뒤 현재 위치에서 리부트를 돌려 미방문만 재구성되는지 확인한다.
 */
@Transactional
class TravelRebootIntegrationTest extends AbstractContainerTest {

  @Autowired private TravelService travelService;
  @Autowired private TravelRebootService travelRebootService;
  @Autowired private UserRepository userRepository;

  @Test
  @DisplayName("방문한 장소를 빼고 현재 위치에서 남은 시간에 담기는 미방문 장소만 재구성한다")
  void rebootReschedulesUnvisitedFromCurrentLocation() {
    User user = userRepository.save(createUser("reboot@example.com", "reboot"));
    AuthenticatedUser auth = AuthenticatedUser.from(user);

    TravelResDto travel = createTravel(user);
    PlanResDto plan =
        travelService.createPlan(
            auth, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));

    // 부산 서→동. 첫 곳(해운대)은 이미 방문 처리.
    PlanPlaceResDto haeundae = createPlace(auth, plan.planId(), 1, "해운대", 35.1587, 129.1604, 60);
    PlanPlaceResDto gwangalli = createPlace(auth, plan.planId(), 2, "광안리", 35.1532, 129.1186, 60);
    PlanPlaceResDto seomyeon = createPlace(auth, plan.planId(), 3, "서면", 35.1580, 129.0596, 60);
    travelService.updatePlanPlaceVisited(
        auth, haeundae.planPlaceId(), new PlanPlaceVisitedUpdateReqDto(true));

    RoutePoint current = RoutePoint.of("현재 위치", 35.2444, 129.2222); // 기장

    TravelRebootResDto result =
        travelRebootService.reboot(
            auth, plan.planId(), current, 600, TransportType.PUBLIC_TRANSPORT);

    assertThat(result.visitedPlaceIds()).containsExactly(haeundae.planPlaceId());
    assertThat(result.reachablePlaceIds())
        .containsExactlyInAnyOrder(gwangalli.planPlaceId(), seomyeon.planPlaceId());
    assertThat(result.droppedForTimePlaceIds()).isEmpty();
    assertThat(result.skippedNoCoordinatesPlaceIds()).isEmpty();
    assertThat(result.orderedPoints().get(0).name()).isEqualTo("현재 위치");
    assertThat(result.orderedPoints()).hasSize(3); // 현재 위치 + 미방문 2곳
    assertThat(result.totalMinutes())
        .isEqualTo(result.totalTravelMinutes() + result.totalStayMinutes());
    assertThat(result.totalMinutes()).isLessThanOrEqualTo(600);
  }

  @Test
  @DisplayName("남은 시간이 짧으면 담기는 곳만 남기고 나머지는 시간 부족으로 제외한다")
  void rebootDropsPlacesThatDoNotFit() {
    User user = userRepository.save(createUser("reboot-tight@example.com", "reboot-tight"));
    AuthenticatedUser auth = AuthenticatedUser.from(user);

    TravelResDto travel = createTravel(user);
    PlanResDto plan =
        travelService.createPlan(
            auth, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    createPlace(auth, plan.planId(), 1, "광안리", 35.1532, 129.1186, 60);
    createPlace(auth, plan.planId(), 2, "서면", 35.1580, 129.0596, 60);
    createPlace(auth, plan.planId(), 3, "남포동", 35.0979, 129.0301, 60);

    RoutePoint current = RoutePoint.of("현재 위치", 35.2444, 129.2222); // 기장

    // 기장→광안리(90)+체류60=150. 200분이면 광안리만 담긴다.
    TravelRebootResDto result =
        travelRebootService.reboot(
            auth, plan.planId(), current, 200, TransportType.PUBLIC_TRANSPORT);

    assertThat(result.reachablePlaceIds()).hasSize(1);
    assertThat(result.droppedForTimePlaceIds()).hasSize(2);
    assertThat(result.totalMinutes()).isLessThanOrEqualTo(200);
  }

  private TravelResDto createTravel(User user) {
    return travelService.createTravel(
        AuthenticatedUser.from(user),
        new TravelCreateReqDto(
            "Busan",
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 3),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null));
  }

  private PlanPlaceResDto createPlace(
      AuthenticatedUser auth,
      UUID planId,
      Integer sequence,
      String name,
      double latitude,
      double longitude,
      Integer durationMinutes) {
    return travelService.createPlanPlace(
        auth,
        planId,
        new PlanPlaceCreateReqDto(
            sequence,
            name,
            "Busan",
            latitude,
            longitude,
            PlaceProvider.GOOGLE,
            name,
            durationMinutes,
            null,
            null,
            false));
  }

  private User createUser(String email, String nickname) {
    return User.builder()
        .email(email)
        .provider("google")
        .providerId("google-" + nickname)
        .name(new Name("Kim", "Tester"))
        .nickname(nickname)
        .role(UserRole.USER)
        .build();
  }
}
