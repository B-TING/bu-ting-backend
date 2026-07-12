package com.butingbe.domain.travelrecord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.dto.request.PlanCreateReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceCreateReqDto;
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
import com.butingbe.domain.travel.service.TravelService;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordCreateReqDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordResDto;
import com.butingbe.domain.travelrecord.entity.TravelRecordStatus;
import com.butingbe.domain.travelrecord.repository.TravelRecordDayRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordPlaceRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordRouteRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.DuplicateResourceException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.support.AbstractContainerTest;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TravelRecordServiceImplTest extends AbstractContainerTest {

  @Autowired private TravelRecordService travelRecordService;
  @Autowired private TravelService travelService;
  @Autowired private UserRepository userRepository;
  @Autowired private PlanPlaceRepository planPlaceRepository;
  @Autowired private PlanRouteRepository planRouteRepository;
  @Autowired private TravelRecordDayRepository travelRecordDayRepository;
  @Autowired private TravelRecordPlaceRepository travelRecordPlaceRepository;
  @Autowired private TravelRecordRouteRepository travelRecordRouteRepository;

  @Test
  @DisplayName("completed travel can be copied to a draft travel record snapshot")
  void createDraftCopiesCompletedTravelItinerary() {
    User user = userRepository.save(createUser("record-owner@example.com", "record-owner"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createCompletedTravel(authenticatedUser);
    PlanResDto firstDay =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    PlanPlaceResDto firstPlace =
        createPlace(authenticatedUser, firstDay.planId(), 1, "Busan Station");
    PlanPlaceResDto secondPlace =
        createPlace(authenticatedUser, firstDay.planId(), 2, "Haeundae");
    saveRoute(firstPlace, secondPlace);

    TravelRecordResDto result =
        travelRecordService.createDraft(
            authenticatedUser,
            travel.id(),
            new TravelRecordCreateReqDto("Summer Busan", "Great trip", "https://image.test/1"));

    assertThat(result.originalTravelId()).isEqualTo(travel.id());
    assertThat(result.authorId()).isEqualTo(user.getId());
    assertThat(result.status()).isEqualTo(TravelRecordStatus.DRAFT);
    assertThat(result.title()).isEqualTo("Summer Busan");
    assertThat(result.days()).hasSize(1);
    assertThat(result.days().getFirst().places()).hasSize(2);
    assertThat(result.days().getFirst().places().getFirst().placeName())
        .isEqualTo("Busan Station");
    assertThat(result.days().getFirst().places().getFirst().routeToNext().transportType())
        .isEqualTo(TransportType.PUBLIC_TRANSPORT);
    assertThat(travelRecordDayRepository.findByTravelRecord_IdOrderByDayNumberAsc(result.travelRecordId()))
        .hasSize(1);
    assertThat(travelRecordPlaceRepository.findByProviderAndProviderPlaceId(
            PlaceProvider.GOOGLE, "Busan Station"))
        .hasSize(1);
    assertThat(travelRecordRouteRepository.findByTravelRecordDay_Id(result.days().getFirst().travelRecordDayId()))
        .hasSize(1);
  }

  @Test
  @DisplayName("same author cannot create duplicate travel records for one travel")
  void createDraftRejectsDuplicateRecord() {
    User user = userRepository.save(createUser("record-duplicate@example.com", "record-duplicate"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createCompletedTravel(authenticatedUser);

    travelRecordService.createDraft(authenticatedUser, travel.id(), null);

    assertThatThrownBy(() -> travelRecordService.createDraft(authenticatedUser, travel.id(), null))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("Travel record already exists.");
  }

  @Test
  @DisplayName("author can get draft travel record snapshot")
  void getDraftReturnsDraftSnapshot() {
    User user = userRepository.save(createUser("record-get@example.com", "record-get"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createCompletedTravel(authenticatedUser);
    PlanResDto firstDay =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    createPlace(authenticatedUser, firstDay.planId(), 1, "Busan Station");
    TravelRecordResDto draft = travelRecordService.createDraft(authenticatedUser, travel.id(), null);

    TravelRecordResDto result =
        travelRecordService.getDraft(authenticatedUser, travel.id(), draft.travelRecordId());

    assertThat(result.travelRecordId()).isEqualTo(draft.travelRecordId());
    assertThat(result.status()).isEqualTo(TravelRecordStatus.DRAFT);
    assertThat(result.days()).hasSize(1);
    assertThat(result.days().getFirst().places().getFirst().placeName())
        .isEqualTo("Busan Station");
  }

  @Test
  @DisplayName("non-author cannot get draft travel record")
  void getDraftRejectsNonAuthor() {
    User owner = userRepository.save(createUser("record-owner-get@example.com", "record-owner-get"));
    User outsider =
        userRepository.save(createUser("record-outsider-get@example.com", "record-outsider-get"));
    AuthenticatedUser ownerUser = AuthenticatedUser.from(owner);
    TravelResDto travel = createCompletedTravel(ownerUser);
    TravelRecordResDto draft = travelRecordService.createDraft(ownerUser, travel.id(), null);

    assertThatThrownBy(
            () ->
                travelRecordService.getDraft(
                    AuthenticatedUser.from(outsider), travel.id(), draft.travelRecordId()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("User is not the travel record author.");
  }

  private TravelResDto createCompletedTravel(AuthenticatedUser authenticatedUser) {
    TravelResDto travel =
        travelService.createTravel(
            authenticatedUser,
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

    return travelService.updateTravelStatus(
        authenticatedUser, travel.id(), new TravelStatusUpdateReqDto(TravelStatus.COMPLETED));
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
            "memo-" + name,
            LocalTime.of(10 + sequence, 0),
            true));
  }

  private void saveRoute(PlanPlaceResDto firstPlace, PlanPlaceResDto secondPlace) {
    var fromPlace = planPlaceRepository.findById(firstPlace.planPlaceId()).orElseThrow();
    var toPlace = planPlaceRepository.findById(secondPlace.planPlaceId()).orElseThrow();

    planRouteRepository.save(
        PlanRoute.builder()
            .plan(fromPlace.getPlan())
            .fromPlace(fromPlace)
            .toPlace(toPlace)
            .transportType(TransportType.PUBLIC_TRANSPORT)
            .durationMinutes(25)
            .distanceMeters(9000)
            .provider(PlaceProvider.GOOGLE)
            .build());
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
