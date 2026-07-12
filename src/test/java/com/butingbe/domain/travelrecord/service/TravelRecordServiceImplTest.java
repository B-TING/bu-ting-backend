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
import com.butingbe.domain.travelrecord.dto.request.PlaceReviewCreateReqDto;
import com.butingbe.domain.travelrecord.dto.request.PlaceReviewUpdateReqDto;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordCreateReqDto;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordUpdateReqDto;
import com.butingbe.domain.travelrecord.dto.response.PlaceReviewResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordResDto;
import com.butingbe.domain.travelrecord.entity.TravelRecordStatus;
import com.butingbe.domain.travelrecord.repository.PlaceReviewRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordDayRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordPlaceRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordRouteRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.DuplicateResourceException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
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
  @Autowired private PlaceReviewRepository placeReviewRepository;

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

  @Test
  @DisplayName("author can update draft travel record content")
  void updateDraftChangesDraftContent() {
    User user = userRepository.save(createUser("record-update@example.com", "record-update"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createCompletedTravel(authenticatedUser);
    TravelRecordResDto draft =
        travelRecordService.createDraft(
            authenticatedUser,
            travel.id(),
            new TravelRecordCreateReqDto("Before", "Before content", "https://image.test/before"));

    TravelRecordResDto result =
        travelRecordService.updateDraft(
            authenticatedUser,
            travel.id(),
            draft.travelRecordId(),
            new TravelRecordUpdateReqDto("After", "After content", "https://image.test/after"));

    assertThat(result.title()).isEqualTo("After");
    assertThat(result.content()).isEqualTo("After content");
    assertThat(result.coverImageUrl()).isEqualTo("https://image.test/after");
    assertThat(result.status()).isEqualTo(TravelRecordStatus.DRAFT);
  }

  @Test
  @DisplayName("draft update keeps existing values when fields are null")
  void updateDraftKeepsExistingValuesForNullFields() {
    User user = userRepository.save(createUser("record-patch@example.com", "record-patch"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createCompletedTravel(authenticatedUser);
    TravelRecordResDto draft =
        travelRecordService.createDraft(
            authenticatedUser,
            travel.id(),
            new TravelRecordCreateReqDto("Before", "Before content", "https://image.test/before"));

    TravelRecordResDto result =
        travelRecordService.updateDraft(
            authenticatedUser,
            travel.id(),
            draft.travelRecordId(),
            new TravelRecordUpdateReqDto(null, "Only content changed", null));

    assertThat(result.title()).isEqualTo("Before");
    assertThat(result.content()).isEqualTo("Only content changed");
    assertThat(result.coverImageUrl()).isEqualTo("https://image.test/before");
  }

  @Test
  @DisplayName("draft update rejects blank title")
  void updateDraftRejectsBlankTitle() {
    User user = userRepository.save(createUser("record-blank@example.com", "record-blank"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createCompletedTravel(authenticatedUser);
    TravelRecordResDto draft = travelRecordService.createDraft(authenticatedUser, travel.id(), null);

    assertThatThrownBy(
            () ->
                travelRecordService.updateDraft(
                    authenticatedUser,
                    travel.id(),
                    draft.travelRecordId(),
                    new TravelRecordUpdateReqDto(" ", null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel record title cannot be blank.");
  }

  @Test
  @DisplayName("author can publish a draft travel record")
  void publishDraftSuccess() {
    User user = userRepository.save(createUser("record-publish@example.com", "record-publish"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);

    TravelRecordResDto result =
        travelRecordService.publish(
            authenticatedUser, draft.originalTravelId(), draft.travelRecordId());

    assertThat(result.travelRecordId()).isEqualTo(draft.travelRecordId());
    assertThat(result.status()).isEqualTo(TravelRecordStatus.PUBLISHED);
    assertThat(result.publishedAt()).isNotNull();
    assertThat(result.days()).hasSize(1);
    assertThatThrownBy(
            () ->
                travelRecordService.getDraft(
                    authenticatedUser, draft.originalTravelId(), draft.travelRecordId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Only draft travel records can be accessed here.");
  }

  @Test
  @DisplayName("non-author cannot publish a travel record")
  void publishRejectsNonAuthor() {
    User owner =
        userRepository.save(createUser("record-publish-owner@example.com", "record-publish-owner"));
    User outsider =
        userRepository.save(
            createUser("record-publish-outsider@example.com", "record-publish-outsider"));
    AuthenticatedUser ownerUser = AuthenticatedUser.from(owner);
    TravelRecordResDto draft = createDraftWithOnePlace(ownerUser);

    assertThatThrownBy(
            () ->
                travelRecordService.publish(
                    AuthenticatedUser.from(outsider),
                    draft.originalTravelId(),
                    draft.travelRecordId()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("User is not the travel record author.");
  }

  @Test
  @DisplayName("published travel record cannot be published again")
  void publishRejectsAlreadyPublishedRecord() {
    User user =
        userRepository.save(createUser("record-publish-again@example.com", "record-publish-again"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);
    travelRecordService.publish(authenticatedUser, draft.originalTravelId(), draft.travelRecordId());

    assertThatThrownBy(
            () ->
                travelRecordService.publish(
                    authenticatedUser, draft.originalTravelId(), draft.travelRecordId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Only draft travel records can be accessed here.");
  }

  @Test
  @DisplayName("travel record without itinerary snapshot cannot be published")
  void publishRejectsEmptyItinerary() {
    User user =
        userRepository.save(createUser("record-publish-empty@example.com", "record-publish-empty"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createCompletedTravel(authenticatedUser);
    TravelRecordResDto draft = travelRecordService.createDraft(authenticatedUser, travel.id(), null);

    assertThatThrownBy(
            () ->
                travelRecordService.publish(
                    authenticatedUser, draft.originalTravelId(), draft.travelRecordId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel record itinerary is required.");
  }

  @Test
  @DisplayName("author can create place review for a draft travel record place")
  void createPlaceReviewSuccess() {
    User user = userRepository.save(createUser("review-owner@example.com", "review-owner"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);
    var place = draft.days().getFirst().places().getFirst();

    PlaceReviewResDto result =
        travelRecordService.createPlaceReview(
            authenticatedUser,
            draft.originalTravelId(),
            draft.travelRecordId(),
            place.travelRecordPlaceId(),
            new PlaceReviewCreateReqDto(5, "Best place in this route"));

    assertThat(result.travelRecordPlaceId()).isEqualTo(place.travelRecordPlaceId());
    assertThat(result.rating()).isEqualTo(5);
    assertThat(result.content()).isEqualTo("Best place in this route");
    assertThat(placeReviewRepository.findByTravelRecordPlace_Id(place.travelRecordPlaceId()))
        .isPresent();
  }

  @Test
  @DisplayName("place review cannot be created twice for the same travel record place")
  void createPlaceReviewRejectsDuplicate() {
    User user = userRepository.save(createUser("review-duplicate@example.com", "review-duplicate"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);
    var place = draft.days().getFirst().places().getFirst();
    travelRecordService.createPlaceReview(
        authenticatedUser,
        draft.originalTravelId(),
        draft.travelRecordId(),
        place.travelRecordPlaceId(),
        new PlaceReviewCreateReqDto(4, "Good"));

    assertThatThrownBy(
            () ->
                travelRecordService.createPlaceReview(
                    authenticatedUser,
                    draft.originalTravelId(),
                    draft.travelRecordId(),
                    place.travelRecordPlaceId(),
                    new PlaceReviewCreateReqDto(5, "Again")))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("Place review already exists.");
  }

  @Test
  @DisplayName("place review rating must be between 1 and 5")
  void createPlaceReviewRejectsInvalidRating() {
    User user = userRepository.save(createUser("review-rating@example.com", "review-rating"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);
    var place = draft.days().getFirst().places().getFirst();

    assertThatThrownBy(
            () ->
                travelRecordService.createPlaceReview(
                    authenticatedUser,
                    draft.originalTravelId(),
                    draft.travelRecordId(),
                    place.travelRecordPlaceId(),
                    new PlaceReviewCreateReqDto(6, "Too high")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Place review rating must be between 1 and 5.");
  }

  @Test
  @DisplayName("author can get place review for a draft travel record place")
  void getPlaceReviewSuccess() {
    User user = userRepository.save(createUser("review-get@example.com", "review-get"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);
    var place = draft.days().getFirst().places().getFirst();
    PlaceReviewResDto created =
        travelRecordService.createPlaceReview(
            authenticatedUser,
            draft.originalTravelId(),
            draft.travelRecordId(),
            place.travelRecordPlaceId(),
            new PlaceReviewCreateReqDto(5, "Worth visiting"));

    PlaceReviewResDto result =
        travelRecordService.getPlaceReview(
            authenticatedUser,
            draft.originalTravelId(),
            draft.travelRecordId(),
            place.travelRecordPlaceId());

    assertThat(result.placeReviewId()).isEqualTo(created.placeReviewId());
    assertThat(result.travelRecordPlaceId()).isEqualTo(place.travelRecordPlaceId());
    assertThat(result.rating()).isEqualTo(5);
    assertThat(result.content()).isEqualTo("Worth visiting");
  }

  @Test
  @DisplayName("place review get returns not found when review does not exist")
  void getPlaceReviewNotFound() {
    User user = userRepository.save(createUser("review-missing@example.com", "review-missing"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);
    var place = draft.days().getFirst().places().getFirst();

    assertThatThrownBy(
            () ->
                travelRecordService.getPlaceReview(
                    authenticatedUser,
                    draft.originalTravelId(),
                    draft.travelRecordId(),
                    place.travelRecordPlaceId()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Place review not found.");
  }

  @Test
  @DisplayName("author can update place review for a draft travel record place")
  void updatePlaceReviewSuccess() {
    User user = userRepository.save(createUser("review-update@example.com", "review-update"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);
    var place = draft.days().getFirst().places().getFirst();
    travelRecordService.createPlaceReview(
        authenticatedUser,
        draft.originalTravelId(),
        draft.travelRecordId(),
        place.travelRecordPlaceId(),
        new PlaceReviewCreateReqDto(3, "Before"));

    PlaceReviewResDto result =
        travelRecordService.updatePlaceReview(
            authenticatedUser,
            draft.originalTravelId(),
            draft.travelRecordId(),
            place.travelRecordPlaceId(),
            new PlaceReviewUpdateReqDto(5, "After"));

    assertThat(result.rating()).isEqualTo(5);
    assertThat(result.content()).isEqualTo("After");
  }

  @Test
  @DisplayName("place review update keeps existing values when fields are null")
  void updatePlaceReviewKeepsExistingValuesForNullFields() {
    User user = userRepository.save(createUser("review-patch@example.com", "review-patch"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);
    var place = draft.days().getFirst().places().getFirst();
    travelRecordService.createPlaceReview(
        authenticatedUser,
        draft.originalTravelId(),
        draft.travelRecordId(),
        place.travelRecordPlaceId(),
        new PlaceReviewCreateReqDto(4, "Before"));

    PlaceReviewResDto result =
        travelRecordService.updatePlaceReview(
            authenticatedUser,
            draft.originalTravelId(),
            draft.travelRecordId(),
            place.travelRecordPlaceId(),
            new PlaceReviewUpdateReqDto(null, "Only content changed"));

    assertThat(result.rating()).isEqualTo(4);
    assertThat(result.content()).isEqualTo("Only content changed");
  }

  @Test
  @DisplayName("place review update returns not found when review does not exist")
  void updatePlaceReviewNotFound() {
    User user =
        userRepository.save(createUser("review-update-missing@example.com", "review-update-missing"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);
    var place = draft.days().getFirst().places().getFirst();

    assertThatThrownBy(
            () ->
                travelRecordService.updatePlaceReview(
                    authenticatedUser,
                    draft.originalTravelId(),
                    draft.travelRecordId(),
                    place.travelRecordPlaceId(),
                    new PlaceReviewUpdateReqDto(5, "Missing")))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Place review not found.");
  }

  @Test
  @DisplayName("place review update rating must be between 1 and 5")
  void updatePlaceReviewRejectsInvalidRating() {
    User user =
        userRepository.save(createUser("review-update-rating@example.com", "review-update-rating"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);
    var place = draft.days().getFirst().places().getFirst();
    travelRecordService.createPlaceReview(
        authenticatedUser,
        draft.originalTravelId(),
        draft.travelRecordId(),
        place.travelRecordPlaceId(),
        new PlaceReviewCreateReqDto(4, "Before"));

    assertThatThrownBy(
            () ->
                travelRecordService.updatePlaceReview(
                    authenticatedUser,
                    draft.originalTravelId(),
                    draft.travelRecordId(),
                    place.travelRecordPlaceId(),
                    new PlaceReviewUpdateReqDto(0, "Invalid")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Place review rating must be between 1 and 5.");
  }

  @Test
  @DisplayName("author can delete place review for a draft travel record place")
  void deletePlaceReviewSuccess() {
    User user = userRepository.save(createUser("review-delete@example.com", "review-delete"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);
    var place = draft.days().getFirst().places().getFirst();
    travelRecordService.createPlaceReview(
        authenticatedUser,
        draft.originalTravelId(),
        draft.travelRecordId(),
        place.travelRecordPlaceId(),
        new PlaceReviewCreateReqDto(5, "Delete me"));

    travelRecordService.deletePlaceReview(
        authenticatedUser,
        draft.originalTravelId(),
        draft.travelRecordId(),
        place.travelRecordPlaceId());

    assertThat(placeReviewRepository.findByTravelRecordPlace_Id(place.travelRecordPlaceId()))
        .isEmpty();
    assertThatThrownBy(
            () ->
                travelRecordService.getPlaceReview(
                    authenticatedUser,
                    draft.originalTravelId(),
                    draft.travelRecordId(),
                    place.travelRecordPlaceId()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Place review not found.");
  }

  @Test
  @DisplayName("place review delete returns not found when review does not exist")
  void deletePlaceReviewNotFound() {
    User user =
        userRepository.save(createUser("review-delete-missing@example.com", "review-delete-missing"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);
    var place = draft.days().getFirst().places().getFirst();

    assertThatThrownBy(
            () ->
                travelRecordService.deletePlaceReview(
                    authenticatedUser,
                    draft.originalTravelId(),
                    draft.travelRecordId(),
                    place.travelRecordPlaceId()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Place review not found.");
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

  private TravelRecordResDto createDraftWithOnePlace(AuthenticatedUser authenticatedUser) {
    TravelResDto travel = createCompletedTravel(authenticatedUser);
    PlanResDto firstDay =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    createPlace(authenticatedUser, firstDay.planId(), 1, "Busan Station");

    return travelRecordService.createDraft(authenticatedUser, travel.id(), null);
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
