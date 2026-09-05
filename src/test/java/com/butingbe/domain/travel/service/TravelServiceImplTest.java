package com.butingbe.domain.travel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.dto.request.PlanCreateReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceCreateReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceSequenceUpdateReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceUpdatePlaceReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceUpdateReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceVisitedUpdateReqDto;
import com.butingbe.domain.travel.dto.request.TravelCreateReqDto;
import com.butingbe.domain.travel.dto.request.TravelStatusUpdateReqDto;
import com.butingbe.domain.travel.dto.response.PlanPlaceResDto;
import com.butingbe.domain.travel.dto.response.PlanResDto;
import com.butingbe.domain.travel.dto.response.TravelPlansResDto;
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
  @DisplayName("plan place visited status can be checked and unchecked")
  void updatePlanPlaceVisitedChangesVisitedStatus() {
    User user = userRepository.save(createUser("visited-place@example.com", "visited-place"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createTravel(user);
    PlanResDto plan =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    PlanPlaceResDto place = createPlace(authenticatedUser, plan.planId(), 1, "Busan Station");

    PlanPlaceResDto checked =
        travelService.updatePlanPlaceVisited(
            authenticatedUser, place.planPlaceId(), new PlanPlaceVisitedUpdateReqDto(true));

    assertThat(checked.visited()).isTrue();
    assertThat(planPlaceRepository.findById(place.planPlaceId()).orElseThrow().getVisited())
        .isTrue();

    PlanPlaceResDto unchecked =
        travelService.updatePlanPlaceVisited(
            authenticatedUser, place.planPlaceId(), new PlanPlaceVisitedUpdateReqDto(false));

    assertThat(unchecked.visited()).isFalse();
    assertThat(planPlaceRepository.findById(place.planPlaceId()).orElseThrow().getVisited())
        .isFalse();
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

  @Test
  @DisplayName("여행 멤버는 일자별 계획과 장소 목록을 조회할 수 있다")
  void getTravelPlansReturnsDaysWithPlaces() {
    User user = userRepository.save(createUser("plans-owner@example.com", "plans-owner"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createTravel(user);
    PlanResDto plan =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    createPlace(authenticatedUser, plan.planId(), 1, "Gwangalli");
    createPlace(authenticatedUser, plan.planId(), 2, "Haeundae");

    var response = travelService.getTravelPlans(authenticatedUser, travel.id());

    assertThat(response.travelId()).isEqualTo(travel.id());
    assertThat(response.title()).isEqualTo("Busan");
    assertThat(response.days()).hasSize(1);
    assertThat(response.days().get(0).dayNumber()).isEqualTo(1);
    List<TravelPlansResDto.PlanPlaceResDto> places = response.days().get(0).places();
    assertThat(places)
        .extracting(TravelPlansResDto.PlanPlaceResDto::placeName)
        .containsExactly("Gwangalli", "Haeundae");
  }

  @Test
  @DisplayName("여행 멤버는 일자를 삭제할 수 있다")
  void deletePlanRemovesPlan() {
    User user = userRepository.save(createUser("delete-plan@example.com", "delete-plan"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createTravel(user);
    PlanResDto plan =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));

    travelService.deletePlan(authenticatedUser, travel.id(), plan.planId());

    assertThat(travelService.getTravelPlans(authenticatedUser, travel.id()).days()).isEmpty();
  }

  @Test
  @DisplayName("장소의 소요 시간·예정 시각·메모를 수정한다")
  void updatePlanPlaceUpdatesSchedule() {
    User user = userRepository.save(createUser("update-schedule@example.com", "update-schedule"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    PlanPlaceResDto place = createPlaceInNewTravel(authenticatedUser, user);

    PlanPlaceResDto updated =
        travelService.updatePlanPlace(
            authenticatedUser,
            place.planPlaceId(),
            new PlanPlaceUpdateReqDto(45, LocalTime.of(13, 30), "Lunch"));

    assertThat(updated.durationMinutes()).isEqualTo(45);
    assertThat(updated.scheduledTime()).isEqualTo(LocalTime.of(13, 30));
    assertThat(updated.memo()).isEqualTo("Lunch");
  }

  @Test
  @DisplayName("장소를 다른 장소로 교체하면 해당 일자의 경로를 지운다")
  void updatePlanPlacePlaceReplacesPlaceAndClearsRoutes() {
    User user = userRepository.save(createUser("replace-place@example.com", "replace-place"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    PlanPlaceResDto place = createPlaceInNewTravel(authenticatedUser, user);

    PlanPlaceResDto updated =
        travelService.updatePlanPlacePlace(
            authenticatedUser,
            place.planPlaceId(),
            new PlanPlaceUpdatePlaceReqDto(
                "Haeundae Beach",
                "Busan Haeundae-gu",
                35.158,
                129.16,
                PlaceProvider.GOOGLE,
                "google-haeundae"));

    assertThat(updated.placeName()).isEqualTo("Haeundae Beach");
    assertThat(updated.address()).isEqualTo("Busan Haeundae-gu");
    assertThat(updated.providerPlaceId()).isEqualTo("google-haeundae");
    assertThat(planRouteRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName("장소 방문 여부를 수정한다")
  void updatePlanPlaceVisitedUpdatesFlag() {
    User user = userRepository.save(createUser("visited@example.com", "visited"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    PlanPlaceResDto place = createPlaceInNewTravel(authenticatedUser, user);

    PlanPlaceResDto updated =
        travelService.updatePlanPlaceVisited(
            authenticatedUser, place.planPlaceId(), new PlanPlaceVisitedUpdateReqDto(true));

    assertThat(updated.visited()).isTrue();
  }

  @Test
  @DisplayName("장소 순서를 재정렬하면 요청한 순서대로 sequence가 다시 매겨진다")
  void updatePlanPlaceSequenceReordersPlaces() {
    User user = userRepository.save(createUser("reorder@example.com", "reorder"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createTravel(user);
    PlanResDto plan =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    PlanPlaceResDto first = createPlace(authenticatedUser, plan.planId(), 1, "First");
    PlanPlaceResDto second = createPlace(authenticatedUser, plan.planId(), 2, "Second");
    PlanPlaceResDto third = createPlace(authenticatedUser, plan.planId(), 3, "Third");

    List<PlanPlaceResDto> reordered =
        travelService.updatePlanPlaceSequence(
            authenticatedUser,
            plan.planId(),
            new PlanPlaceSequenceUpdateReqDto(
                List.of(third.planPlaceId(), first.planPlaceId(), second.planPlaceId())));

    assertThat(reordered)
        .extracting(PlanPlaceResDto::placeName)
        .containsExactly("Third", "First", "Second");
    List<PlanPlaceResDto> stored = travelService.getPlanPlaces(authenticatedUser, plan.planId());
    assertThat(stored).extracting(PlanPlaceResDto::sequence).containsExactly(1, 2, 3);
  }

  @Test
  @DisplayName("장소를 삭제하면 뒤 장소들의 sequence가 앞으로 당겨진다")
  void deletePlanPlaceCompactsSequences() {
    User user = userRepository.save(createUser("delete-place@example.com", "delete-place"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createTravel(user);
    PlanResDto plan =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    PlanPlaceResDto first = createPlace(authenticatedUser, plan.planId(), 1, "First");
    createPlace(authenticatedUser, plan.planId(), 2, "Second");
    createPlace(authenticatedUser, plan.planId(), 3, "Third");

    travelService.deletePlanPlace(authenticatedUser, first.planPlaceId());

    List<PlanPlaceResDto> remaining = travelService.getPlanPlaces(authenticatedUser, plan.planId());
    assertThat(remaining).extracting(PlanPlaceResDto::placeName).containsExactly("Second", "Third");
    assertThat(remaining).extracting(PlanPlaceResDto::sequence).containsExactly(1, 2);
  }

  @Test
  @DisplayName("여행 기간을 벗어난 날짜로는 일자를 만들 수 없다")
  void createPlanRejectsVisitDateOutsideTravelPeriod() {
    User user = userRepository.save(createUser("plan-date@example.com", "plan-date"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createTravel(user);

    assertThatThrownBy(
            () ->
                travelService.createPlan(
                    authenticatedUser,
                    travel.id(),
                    new PlanCreateReqDto(1, LocalDate.of(2026, 7, 31))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Plan visit date must be within the travel period.");
  }

  @Test
  @DisplayName("같은 dayNumber로 일자를 두 번 만들 수 없다")
  void createPlanRejectsDuplicateDayNumber() {
    User user = userRepository.save(createUser("plan-day@example.com", "plan-day"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createTravel(user);
    travelService.createPlan(
        authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));

    assertThatThrownBy(
            () ->
                travelService.createPlan(
                    authenticatedUser,
                    travel.id(),
                    new PlanCreateReqDto(1, LocalDate.of(2026, 8, 2))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Plan day number already exists.");
  }

  @Test
  @DisplayName("여행 상태는 PLANNED로 되돌릴 수 없고, 그 외 전이는 허용한다")
  void updateTravelStatusEnforcesAllowedTransitions() {
    User user = userRepository.save(createUser("status@example.com", "status"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createTravel(user);

    assertThat(
            travelService
                .updateTravelStatus(
                    authenticatedUser,
                    travel.id(),
                    new TravelStatusUpdateReqDto(TravelStatus.PLANNED))
                .status())
        .isEqualTo(TravelStatus.PLANNED);

    travelService.updateTravelStatus(
        authenticatedUser, travel.id(), new TravelStatusUpdateReqDto(TravelStatus.IN_PROGRESS));
    travelService.updateTravelStatus(
        authenticatedUser, travel.id(), new TravelStatusUpdateReqDto(TravelStatus.COMPLETED));
    assertThat(
            travelService
                .updateTravelStatus(
                    authenticatedUser,
                    travel.id(),
                    new TravelStatusUpdateReqDto(TravelStatus.IN_PROGRESS))
                .status())
        .isEqualTo(TravelStatus.IN_PROGRESS);

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
  @DisplayName("이미 사용 중인 sequence로는 장소를 추가할 수 없다")
  void createPlanPlaceRejectsDuplicateSequence() {
    User user = userRepository.save(createUser("sequence@example.com", "sequence"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createTravel(user);
    PlanResDto plan =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    createPlace(authenticatedUser, plan.planId(), 1, "First");

    assertThatThrownBy(() -> createPlace(authenticatedUser, plan.planId(), 1, "Duplicate"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Plan place sequence already exists.");
  }

  @Test
  @DisplayName("sequence를 생략하면 마지막 장소 다음 번호가 자동으로 붙는다")
  void createPlanPlaceAppendsWhenSequenceIsOmitted() {
    User user = userRepository.save(createUser("auto-seq@example.com", "auto-seq"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createTravel(user);
    PlanResDto plan =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    createPlace(authenticatedUser, plan.planId(), 1, "First");

    PlanPlaceResDto appended = createPlace(authenticatedUser, plan.planId(), null, "Second");

    assertThat(appended.sequence()).isEqualTo(2);
  }

  @Test
  @DisplayName("재정렬 요청의 장소 id 목록이 실제와 다르면 거부한다")
  void updatePlanPlaceSequenceRejectsMismatchedIds() {
    User user = userRepository.save(createUser("reorder-invalid@example.com", "reorder-invalid"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createTravel(user);
    PlanResDto plan =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    PlanPlaceResDto first = createPlace(authenticatedUser, plan.planId(), 1, "First");
    createPlace(authenticatedUser, plan.planId(), 2, "Second");

    assertThatThrownBy(
            () ->
                travelService.updatePlanPlaceSequence(
                    authenticatedUser,
                    plan.planId(),
                    new PlanPlaceSequenceUpdateReqDto(List.of(first.planPlaceId()))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("All plan place ids must be included.");

    assertThatThrownBy(
            () ->
                travelService.updatePlanPlaceSequence(
                    authenticatedUser,
                    plan.planId(),
                    new PlanPlaceSequenceUpdateReqDto(
                        List.of(first.planPlaceId(), first.planPlaceId()))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Duplicated plan place id exists.");

    assertThatThrownBy(
            () ->
                travelService.updatePlanPlaceSequence(
                    authenticatedUser,
                    plan.planId(),
                    new PlanPlaceSequenceUpdateReqDto(
                        List.of(first.planPlaceId(), java.util.UUID.randomUUID()))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Plan place ids do not match this plan.");
  }

  private PlanPlaceResDto createPlaceInNewTravel(AuthenticatedUser authenticatedUser, User user) {
    TravelResDto travel = createTravel(user);
    PlanResDto plan =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    return createPlace(authenticatedUser, plan.planId(), 1, "Gwangalli");
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
