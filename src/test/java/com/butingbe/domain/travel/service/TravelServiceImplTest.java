package com.butingbe.domain.travel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.dto.request.PlanCreateReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceCreateReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceSequenceUpdateReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceUpdatePlaceReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceUpdateReqDto;
import com.butingbe.domain.travel.dto.request.TravelCreateReqDto;
import com.butingbe.domain.travel.dto.request.TravelStatusUpdateReqDto;
import com.butingbe.domain.travel.dto.response.PlanPlaceResDto;
import com.butingbe.domain.travel.dto.response.PlanResDto;
import com.butingbe.domain.travel.dto.response.TravelResDto;
import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travel.entity.PlanRoute;
import com.butingbe.domain.travel.entity.TransportType;
import com.butingbe.domain.travel.entity.TravelStatus;
import com.butingbe.domain.travel.repository.PlanPlaceRepository;
import com.butingbe.domain.travel.repository.PlanRouteRepository;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.support.AbstractContainerTest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TravelServiceImplTest extends AbstractContainerTest {

  @Autowired private TravelService travelService;
  @Autowired private UserRepository userRepository;
  @Autowired private TravelRepository travelRepository;
  @Autowired private PlanPlaceRepository planPlaceRepository;
  @Autowired private PlanRouteRepository planRouteRepository;

  @Test
  @DisplayName("여행 종료일이 시작일보다 빠르면 여행을 생성하지 않는다")
  void createTravelInvalidDateThrowsException() {
    User user = userRepository.save(createUser("invalid-date@example.com", "invalid-date"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelCreateReqDto request =
        new TravelCreateReqDto(
            "Invalid",
            LocalDate.of(2026, 8, 3),
            LocalDate.of(2026, 8, 1),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    assertThatThrownBy(() -> travelService.createTravel(authenticatedUser, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel end date cannot be before start date.");
    assertThat(travelRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName("여행 멤버가 아니면 여행 일정 조회를 막는다")
  void getTravelPlansForbiddenWhenUserIsNotMember() {
    User owner = userRepository.save(createUser("owner@example.com", "owner"));
    User outsider = userRepository.save(createUser("outsider@example.com", "outsider"));
    TravelResDto travel = createTravel(owner);

    assertThatThrownBy(
            () -> travelService.getTravelPlans(AuthenticatedUser.from(outsider), travel.id()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("User is not a travel member.");
  }

  @Test
  @DisplayName("존재하지 않는 plan 장소 목록 조회는 404 예외로 분리한다")
  void getPlanPlacesNotFound() {
    User user = userRepository.save(createUser("not-found@example.com", "not-found"));

    assertThatThrownBy(
            () ->
                travelService.getPlanPlaces(
                    AuthenticatedUser.from(user), java.util.UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Plan not found.");
  }

  @Test
  @DisplayName("장소 순서 배열을 받아 sequence를 한 번에 재배치하고 route를 비운다")
  void updatePlanPlaceSequenceSuccess() {
    User user = userRepository.save(createUser("reorder@example.com", "reorder"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createTravel(user);
    PlanResDto plan =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    PlanPlaceResDto first = createPlace(authenticatedUser, plan.planId(), 1, "A");
    PlanPlaceResDto second = createPlace(authenticatedUser, plan.planId(), 2, "B");
    PlanPlaceResDto third = createPlace(authenticatedUser, plan.planId(), 3, "C");

    List<PlanPlaceResDto> result =
        travelService.updatePlanPlaceSequence(
            authenticatedUser,
            plan.planId(),
            new PlanPlaceSequenceUpdateReqDto(
                List.of(third.planPlaceId(), first.planPlaceId(), second.planPlaceId())));

    assertThat(result)
        .extracting(PlanPlaceResDto::planPlaceId)
        .containsExactly(third.planPlaceId(), first.planPlaceId(), second.planPlaceId());
    assertThat(planPlaceRepository.findByPlan_IdOrderBySequenceAsc(plan.planId()))
        .extracting(place -> place.getId())
        .containsExactly(third.planPlaceId(), first.planPlaceId(), second.planPlaceId());
    assertThat(planRouteRepository.findByPlan_Id(plan.planId())).isEmpty();
  }

  @Test
  @DisplayName("plan place update changes duration, scheduled time, and memo")
  void updatePlanPlaceChangesScheduleFields() {
    User user = userRepository.save(createUser("update-place@example.com", "update-place"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createTravel(user);
    PlanResDto plan =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    PlanPlaceResDto place = createPlace(authenticatedUser, plan.planId(), 1, "Busan Station");

    PlanPlaceResDto result =
        travelService.updatePlanPlace(
            authenticatedUser,
            place.planPlaceId(),
            new PlanPlaceUpdateReqDto(90, LocalTime.of(11, 30), "Lunch before beach"));

    assertThat(result.durationMinutes()).isEqualTo(90);
    assertThat(result.scheduledTime()).isEqualTo(LocalTime.of(11, 30));
    assertThat(result.memo()).isEqualTo("Lunch before beach");
  }

  @Test
  @DisplayName("travel member can update travel status")
  void updateTravelStatusChangesStatus() {
    User user = userRepository.save(createUser("complete@example.com", "complete"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createTravel(user);

    TravelResDto result =
        travelService.updateTravelStatus(
            authenticatedUser, travel.id(), new TravelStatusUpdateReqDto(TravelStatus.IN_PROGRESS));

    assertThat(result.status()).isEqualTo(TravelStatus.IN_PROGRESS);
    assertThat(travelRepository.findById(travel.id()).orElseThrow().getStatus())
        .isEqualTo(TravelStatus.IN_PROGRESS);
  }

  @Test
  @DisplayName("travel status update rejects users who are not travel members")
  void updateTravelStatusForbiddenWhenUserIsNotMember() {
    User owner = userRepository.save(createUser("complete-owner@example.com", "complete-owner"));
    User outsider =
        userRepository.save(createUser("complete-outsider@example.com", "complete-outsider"));
    TravelResDto travel = createTravel(owner);

    assertThatThrownBy(
            () ->
                travelService.updateTravelStatus(
                    AuthenticatedUser.from(outsider),
                    travel.id(),
                    new TravelStatusUpdateReqDto(TravelStatus.COMPLETED)))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("User is not a travel member.");
  }

  @Test
  @DisplayName("travel status cannot be changed back to planned")
  void updateTravelStatusRejectsBackToPlanned() {
    User user = userRepository.save(createUser("planned-back@example.com", "planned-back"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createTravel(user);
    travelService.updateTravelStatus(
        authenticatedUser, travel.id(), new TravelStatusUpdateReqDto(TravelStatus.COMPLETED));

    assertThatThrownBy(
            () ->
                travelService.updateTravelStatus(
                    authenticatedUser,
                    travel.id(),
                    new TravelStatusUpdateReqDto(TravelStatus.PLANNED)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel status cannot be changed back to PLANNED.");
  }

  @Test
  @DisplayName("completed travel can be restored to in progress")
  void updateTravelStatusAllowsCompletedToInProgress() {
    User user = userRepository.save(createUser("restore-progress@example.com", "restore-progress"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createTravel(user);
    travelService.updateTravelStatus(
        authenticatedUser, travel.id(), new TravelStatusUpdateReqDto(TravelStatus.COMPLETED));

    TravelResDto result =
        travelService.updateTravelStatus(
            authenticatedUser, travel.id(), new TravelStatusUpdateReqDto(TravelStatus.IN_PROGRESS));

    assertThat(result.status()).isEqualTo(TravelStatus.IN_PROGRESS);
  }

  @Test
  @DisplayName("plan place update place changes location fields and clears routes")
  void updatePlanPlacePlaceChangesLocationFields() {
    User user = userRepository.save(createUser("replace-place@example.com", "replace-place"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createTravel(user);
    PlanResDto plan =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    PlanPlaceResDto first = createPlace(authenticatedUser, plan.planId(), 1, "Busan Station");
    PlanPlaceResDto second = createPlace(authenticatedUser, plan.planId(), 2, "Haeundae");
    var firstPlace = planPlaceRepository.findById(first.planPlaceId()).orElseThrow();
    var secondPlace = planPlaceRepository.findById(second.planPlaceId()).orElseThrow();
    planRouteRepository.save(
        PlanRoute.builder()
            .plan(firstPlace.getPlan())
            .fromPlace(firstPlace)
            .toPlace(secondPlace)
            .transportType(TransportType.CAR)
            .durationMinutes(35)
            .distanceMeters(12000)
            .provider(PlaceProvider.GOOGLE)
            .build());

    PlanPlaceResDto result =
        travelService.updatePlanPlacePlace(
            authenticatedUser,
            first.planPlaceId(),
            new PlanPlaceUpdatePlaceReqDto(
                "Gwangalli",
                "Busan Suyeong-gu",
                35.153,
                129.118,
                PlaceProvider.KAKAO,
                "kakao-gwangalli-id"));

    assertThat(result.placeName()).isEqualTo("Gwangalli");
    assertThat(result.address()).isEqualTo("Busan Suyeong-gu");
    assertThat(result.latitude()).isEqualTo(35.153);
    assertThat(result.longitude()).isEqualTo(129.118);
    assertThat(result.provider()).isEqualTo(PlaceProvider.KAKAO);
    assertThat(result.providerPlaceId()).isEqualTo("kakao-gwangalli-id");
    assertThat(planRouteRepository.findByPlan_Id(plan.planId())).isEmpty();
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
      AuthenticatedUser authenticatedUser, java.util.UUID planId, Integer sequence, String name) {
    return travelService.createPlanPlace(
        authenticatedUser,
        planId,
        new PlanPlaceCreateReqDto(
            sequence,
            name,
            "Busan",
            35.115,
            129.041,
            PlaceProvider.GOOGLE,
            name,
            30,
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
