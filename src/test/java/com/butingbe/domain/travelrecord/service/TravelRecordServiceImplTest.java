package com.butingbe.domain.travelrecord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.file.dto.FileUploadResDto;
import com.butingbe.domain.file.service.FileStorageService;
import com.butingbe.domain.travel.dto.request.PlanCreateReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceCreateReqDto;
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
import com.butingbe.domain.travel.service.TravelService;
import com.butingbe.domain.travelrecord.dto.request.PlaceReviewCreateReqDto;
import com.butingbe.domain.travelrecord.dto.request.PlaceReviewUpdateReqDto;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordCloneToTravelReqDto;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordCommentCreateReqDto;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordCommentUpdateReqDto;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordCreateReqDto;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordFeedSort;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordUpdateReqDto;
import com.butingbe.domain.travelrecord.dto.response.PlaceReviewResDto;
import com.butingbe.domain.travelrecord.dto.response.PlaceReviewSummaryResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordBookmarkResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordCommentResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordFeedPageResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordFeedResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordLikeResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordManageResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordResDto;
import com.butingbe.domain.travelrecord.entity.TravelRecordStatus;
import com.butingbe.domain.travelrecord.repository.PlaceReviewRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordDayRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordPlaceRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordRouteRepository;
import com.butingbe.domain.travelteam.entity.TravelTeamRole;
import com.butingbe.domain.travelteam.repository.TravelMemberRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.DuplicateResourceException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import com.butingbe.support.AbstractContainerTest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Transactional
@Import(TravelRecordServiceImplTest.FileStorageTestConfig.class)
class TravelRecordServiceImplTest extends AbstractContainerTest {

  @TestConfiguration
  static class FileStorageTestConfig {

    @Bean
    @Primary
    FileStorageService fileStorageService() {
      return new FileStorageService() {
        @Override
        public FileUploadResDto upload(MultipartFile file) {
          throw new UnsupportedOperationException();
        }

        @Override
        public String getPresignedUrl(String fileKey) {
          return "https://signed.example.com/" + fileKey;
        }

        @Override
        public void delete(String fileKey) {}
      };
    }
  }

  @Autowired private TravelRecordService travelRecordService;
  @Autowired private TravelService travelService;
  @Autowired private UserRepository userRepository;
  @Autowired private PlanPlaceRepository planPlaceRepository;
  @Autowired private PlanRouteRepository planRouteRepository;
  @Autowired private TravelRecordRepository travelRecordRepository;
  @Autowired private TravelRecordDayRepository travelRecordDayRepository;
  @Autowired private TravelRecordPlaceRepository travelRecordPlaceRepository;
  @Autowired private TravelRecordRouteRepository travelRecordRouteRepository;
  @Autowired private PlaceReviewRepository placeReviewRepository;
  @Autowired private TravelMemberRepository travelMemberRepository;

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
    PlanPlaceResDto secondPlace = createPlace(authenticatedUser, firstDay.planId(), 2, "Haeundae");
    saveRoute(firstPlace, secondPlace);

    TravelRecordResDto result =
        travelRecordService.createDraft(
            authenticatedUser,
            travel.id(),
            new TravelRecordCreateReqDto("Summer Busan", "Great trip", "https://image.test/1", 5));

    assertThat(result.originalTravelId()).isEqualTo(travel.id());
    assertThat(result.authorId()).isEqualTo(user.getId());
    assertThat(result.status()).isEqualTo(TravelRecordStatus.DRAFT);
    assertThat(result.title()).isEqualTo("Summer Busan");
    assertThat(result.overallRating()).isEqualTo(5);
    assertThat(result.days()).hasSize(1);
    assertThat(result.days().getFirst().places()).hasSize(2);
    assertThat(result.days().getFirst().places().getFirst().placeName()).isEqualTo("Busan Station");
    assertThat(result.days().getFirst().places().getFirst().routeToNext().transportType())
        .isEqualTo(TransportType.PUBLIC_TRANSPORT);
    assertThat(
            travelRecordDayRepository.findByTravelRecord_IdOrderByDayNumberAsc(
                result.travelRecordId()))
        .hasSize(1);
    assertThat(
            travelRecordPlaceRepository.findByProviderAndProviderPlaceId(
                PlaceProvider.GOOGLE, "Busan Station"))
        .hasSize(1);
    assertThat(
            travelRecordRouteRepository.findByTravelRecordDay_Id(
                result.days().getFirst().travelRecordDayId()))
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
    TravelRecordResDto draft =
        travelRecordService.createDraft(authenticatedUser, travel.id(), null);

    TravelRecordResDto result =
        travelRecordService.getDraft(authenticatedUser, travel.id(), draft.travelRecordId());

    assertThat(result.travelRecordId()).isEqualTo(draft.travelRecordId());
    assertThat(result.status()).isEqualTo(TravelRecordStatus.DRAFT);
    assertThat(result.days()).hasSize(1);
    assertThat(result.days().getFirst().places().getFirst().placeName()).isEqualTo("Busan Station");
  }

  @Test
  @DisplayName("non-author cannot get draft travel record")
  void getDraftRejectsNonAuthor() {
    User owner =
        userRepository.save(createUser("record-owner-get@example.com", "record-owner-get"));
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
    TravelRecordResDto draft =
        travelRecordService.createDraft(authenticatedUser, travel.id(), null);

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
    travelRecordService.publish(
        authenticatedUser, draft.originalTravelId(), draft.travelRecordId());

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
    TravelRecordResDto draft =
        travelRecordService.createDraft(authenticatedUser, travel.id(), null);

    assertThatThrownBy(
            () ->
                travelRecordService.publish(
                    authenticatedUser, draft.originalTravelId(), draft.travelRecordId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel record itinerary is required.");
  }

  @Test
  @DisplayName("travel record without overall rating cannot be published")
  void publishRejectsMissingOverallRating() {
    User user =
        userRepository.save(createUser("record-publish-rating@example.com", "record-rating"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createCompletedTravel(authenticatedUser);
    PlanResDto firstDay =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    createPlace(authenticatedUser, firstDay.planId(), 1, "Busan Station", "Busan");
    TravelRecordResDto draft =
        travelRecordService.createDraft(
            authenticatedUser,
            travel.id(),
            new TravelRecordCreateReqDto("No Rating", "Missing rating", null));

    assertThatThrownBy(
            () ->
                travelRecordService.publish(
                    authenticatedUser, draft.originalTravelId(), draft.travelRecordId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel record overall rating is required.");
  }

  @Test
  @DisplayName("published travel record can be viewed publicly")
  void getPublishedSuccess() {
    User user =
        userRepository.save(createUser("record-public-detail@example.com", "record-public-detail"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);
    TravelRecordResDto published =
        travelRecordService.publish(
            authenticatedUser, draft.originalTravelId(), draft.travelRecordId());

    TravelRecordResDto result = travelRecordService.getPublished(published.travelRecordId());

    assertThat(result.travelRecordId()).isEqualTo(published.travelRecordId());
    assertThat(result.authorId()).isEqualTo(user.getId());
    assertThat(result.status()).isEqualTo(TravelRecordStatus.PUBLISHED);
    assertThat(result.publishedAt()).isNotNull();
    assertThat(result.days()).hasSize(1);
    assertThat(result.days().getFirst().places().getFirst().placeName()).isEqualTo("Busan Station");
  }

  @Test
  @DisplayName("draft travel record cannot be viewed publicly")
  void getPublishedRejectsDraft() {
    User user =
        userRepository.save(createUser("record-public-draft@example.com", "record-public-draft"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);

    assertThatThrownBy(() -> travelRecordService.getPublished(draft.travelRecordId()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Travel record not found.");
  }

  @Test
  @DisplayName("user can clone a published travel record itinerary to a new travel plan")
  void clonePublishedTravelRecordToTravelPlan() {
    User author =
        userRepository.save(createUser("record-clone-author@example.com", "clone-author"));
    User user = userRepository.save(createUser("record-clone-user@example.com", "clone-user"));
    AuthenticatedUser authorUser = AuthenticatedUser.from(author);
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto sourceTravel = createCompletedTravel(authorUser);
    PlanResDto firstDay =
        travelService.createPlan(
            authorUser, sourceTravel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    PlanResDto secondDay =
        travelService.createPlan(
            authorUser, sourceTravel.id(), new PlanCreateReqDto(2, LocalDate.of(2026, 8, 2)));
    PlanPlaceResDto firstPlace = createPlace(authorUser, firstDay.planId(), 1, "Busan Station");
    PlanPlaceResDto secondPlace = createPlace(authorUser, firstDay.planId(), 2, "Haeundae");
    createPlace(authorUser, secondDay.planId(), 1, "Gwangalli");
    saveRoute(firstPlace, secondPlace);
    TravelRecordResDto draft =
        travelRecordService.createDraft(
            authorUser,
            sourceTravel.id(),
            new TravelRecordCreateReqDto("Busan Course", "Nice route", null, 5));
    TravelRecordResDto published =
        travelRecordService.publish(authorUser, sourceTravel.id(), draft.travelRecordId());

    TravelPlansResDto result =
        travelRecordService.cloneToTravel(
            authenticatedUser,
            published.travelRecordId(),
            new TravelRecordCloneToTravelReqDto(
                "Copied Busan",
                LocalDate.of(2026, 9, 10),
                true,
                false,
                null,
                null,
                null,
                2,
                "seafood",
                null,
                "Haeundae"));

    assertThat(result.travelId()).isNotEqualTo(sourceTravel.id());
    assertThat(result.title()).isEqualTo("Copied Busan");
    assertThat(result.days()).hasSize(2);
    assertThat(result.days())
        .extracting(TravelPlansResDto.PlanDayResDto::visitDate)
        .containsExactly(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11));
    assertThat(result.days().getFirst().places()).hasSize(2);
    assertThat(result.days().getFirst().places().getFirst().placeName()).isEqualTo("Busan Station");
    assertThat(result.days().getFirst().places().getFirst().visited()).isFalse();
    assertThat(result.days().getFirst().places().getFirst().routeToNext()).isNotNull();
    assertThat(result.days().getFirst().places().getFirst().routeToNext().durationMinutes())
        .isEqualTo(25);
    assertThat(
            travelMemberRepository
                .findByTravel_IdAndUser_Id(result.travelId(), user.getId())
                .orElseThrow()
                .getRole())
        .isEqualTo(TravelTeamRole.LEADER);
  }

  @Test
  @DisplayName("non-published travel record cannot be cloned to a travel plan")
  void cloneToTravelRejectsNonPublishedRecord() {
    User user = userRepository.save(createUser("record-clone-hidden@example.com", "clone-hidden"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Hidden Course");

    assertThatThrownBy(
            () ->
                travelRecordService.cloneToTravel(
                    authenticatedUser,
                    draft.travelRecordId(),
                    new TravelRecordCloneToTravelReqDto(
                        "Copied",
                        LocalDate.of(2026, 9, 10),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Travel record not found.");
  }

  @Test
  @DisplayName("clone trims long record title when travel title is omitted")
  void cloneToTravelTrimsLongRecordTitleWhenTitleIsOmitted() {
    User user = userRepository.save(createUser("record-clone-title@example.com", "clone-title"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft =
        createDraftWithOnePlace(authenticatedUser, "Very Long Busan Travel Record Title");
    TravelRecordResDto published =
        travelRecordService.publish(
            authenticatedUser, draft.originalTravelId(), draft.travelRecordId());

    TravelPlansResDto result =
        travelRecordService.cloneToTravel(
            authenticatedUser,
            published.travelRecordId(),
            new TravelRecordCloneToTravelReqDto(
                null,
                LocalDate.of(2026, 9, 10),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

    assertThat(result.title()).hasSize(15);
    assertThat(result.title()).isEqualTo("Very Long Busan");
  }

  @Test
  @DisplayName("latest feed returns only published travel records in newest order")
  void getLatestFeedReturnsPublishedRecordsInNewestOrder() {
    User olderUser =
        userRepository.save(createUser("record-feed-older@example.com", "record-feed-older"));
    User newerUser =
        userRepository.save(createUser("record-feed-newer@example.com", "record-feed-newer"));
    User draftUser =
        userRepository.save(createUser("record-feed-draft@example.com", "record-feed-draft"));
    AuthenticatedUser olderAuthenticatedUser = AuthenticatedUser.from(olderUser);
    AuthenticatedUser newerAuthenticatedUser = AuthenticatedUser.from(newerUser);
    AuthenticatedUser draftAuthenticatedUser = AuthenticatedUser.from(draftUser);
    TravelRecordResDto olderDraft = createDraftWithOnePlace(olderAuthenticatedUser, "Older Feed");
    TravelRecordResDto newerDraft = createDraftWithOnePlace(newerAuthenticatedUser, "Newer Feed");
    TravelRecordResDto hiddenDraft = createDraftWithOnePlace(draftAuthenticatedUser, "Draft Feed");
    TravelRecordResDto olderPublished =
        travelRecordService.publish(
            olderAuthenticatedUser, olderDraft.originalTravelId(), olderDraft.travelRecordId());
    TravelRecordResDto newerPublished =
        travelRecordService.publish(
            newerAuthenticatedUser, newerDraft.originalTravelId(), newerDraft.travelRecordId());

    TravelRecordFeedPageResDto result = travelRecordService.getLatestFeed(null, null);

    assertThat(result.items())
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .containsExactly(newerPublished.travelRecordId(), olderPublished.travelRecordId());
    assertThat(result.items())
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .doesNotContain(hiddenDraft.travelRecordId());
    assertThat(result.hasNext()).isFalse();
    assertThat(result.nextCursor()).isNull();
    assertThat(result.items().getFirst().title()).isEqualTo("Newer Feed");
    assertThat(result.items().getFirst().authorNickname()).isEqualTo("record-feed-newer");
    assertThat(result.items().getFirst().publishedAt()).isNotNull();
  }

  @Test
  @DisplayName("latest feed supports cursor pagination")
  void getLatestFeedSupportsCursorPagination() {
    User firstUser =
        userRepository.save(
            createUser("record-feed-page-first@example.com", "record-feed-page-first"));
    User secondUser =
        userRepository.save(
            createUser("record-feed-page-second@example.com", "record-feed-page-second"));
    AuthenticatedUser firstAuthenticatedUser = AuthenticatedUser.from(firstUser);
    AuthenticatedUser secondAuthenticatedUser = AuthenticatedUser.from(secondUser);
    TravelRecordResDto firstDraft =
        createDraftWithOnePlace(firstAuthenticatedUser, "First Page Feed");
    TravelRecordResDto secondDraft =
        createDraftWithOnePlace(secondAuthenticatedUser, "Second Page Feed");
    TravelRecordResDto firstPublished =
        travelRecordService.publish(
            firstAuthenticatedUser, firstDraft.originalTravelId(), firstDraft.travelRecordId());
    TravelRecordResDto secondPublished =
        travelRecordService.publish(
            secondAuthenticatedUser, secondDraft.originalTravelId(), secondDraft.travelRecordId());

    TravelRecordFeedPageResDto firstPage = travelRecordService.getLatestFeed(null, 1);
    TravelRecordFeedPageResDto secondPage =
        travelRecordService.getLatestFeed(firstPage.nextCursor(), 1);

    assertThat(firstPage.items())
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .containsExactly(secondPublished.travelRecordId());
    assertThat(firstPage.hasNext()).isTrue();
    assertThat(firstPage.nextCursor()).isNotBlank();
    assertThat(secondPage.items())
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .containsExactly(firstPublished.travelRecordId());
    assertThat(secondPage.hasNext()).isFalse();
    assertThat(secondPage.nextCursor()).isNull();
  }

  @Test
  @DisplayName("latest feed searches by title and place name")
  void getLatestFeedSearchesByKeyword() {
    User titleUser =
        userRepository.save(createUser("record-search-title@example.com", "record-search-title"));
    User placeUser =
        userRepository.save(createUser("record-search-place@example.com", "record-search-place"));
    AuthenticatedUser titleAuthenticatedUser = AuthenticatedUser.from(titleUser);
    AuthenticatedUser placeAuthenticatedUser = AuthenticatedUser.from(placeUser);
    TravelRecordResDto cafeDraft =
        createDraftWithOnePlace(titleAuthenticatedUser, "Hidden Cafe Route");
    TravelRecordResDto beachDraft =
        createDraftWithOnePlace(placeAuthenticatedUser, "Summer Route", "Haeundae Beach");
    TravelRecordResDto cafePublished =
        travelRecordService.publish(
            titleAuthenticatedUser, cafeDraft.originalTravelId(), cafeDraft.travelRecordId());
    TravelRecordResDto beachPublished =
        travelRecordService.publish(
            placeAuthenticatedUser, beachDraft.originalTravelId(), beachDraft.travelRecordId());

    TravelRecordFeedPageResDto titleResult =
        travelRecordService.getLatestFeed(null, null, "cafe", null, null, null, null);
    TravelRecordFeedPageResDto placeResult =
        travelRecordService.getLatestFeed(null, null, "haeundae", null, null, null, null);

    assertThat(titleResult.items())
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .contains(cafePublished.travelRecordId())
        .doesNotContain(beachPublished.travelRecordId());
    assertThat(placeResult.items())
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .contains(beachPublished.travelRecordId())
        .doesNotContain(cafePublished.travelRecordId());
  }

  @Test
  @DisplayName("latest feed filters by place and travel date range")
  void getLatestFeedFiltersByPlaceAndTravelDateRange() {
    User stationUser =
        userRepository.save(
            createUser("record-filter-station@example.com", "record-filter-station"));
    User beachUser =
        userRepository.save(createUser("record-filter-beach@example.com", "record-filter-beach"));
    AuthenticatedUser stationAuthenticatedUser = AuthenticatedUser.from(stationUser);
    AuthenticatedUser beachAuthenticatedUser = AuthenticatedUser.from(beachUser);
    TravelRecordResDto stationDraft =
        createDraftWithOnePlace(stationAuthenticatedUser, "Station Route", "Busan Station");
    TravelRecordResDto beachDraft =
        createDraftWithOnePlace(beachAuthenticatedUser, "Beach Route", "Haeundae");
    TravelRecordResDto stationPublished =
        travelRecordService.publish(
            stationAuthenticatedUser,
            stationDraft.originalTravelId(),
            stationDraft.travelRecordId());
    TravelRecordResDto beachPublished =
        travelRecordService.publish(
            beachAuthenticatedUser, beachDraft.originalTravelId(), beachDraft.travelRecordId());

    TravelRecordFeedPageResDto placeResult =
        travelRecordService.getLatestFeed(
            (AuthenticatedUser) null,
            null,
            null,
            null,
            "Busan Station",
            null,
            null,
            null,
            null,
            null);
    TravelRecordFeedPageResDto overlappingDateResult =
        travelRecordService.getLatestFeed(
            null, null, null, null, null, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 2));
    TravelRecordFeedPageResDto outOfRangeDateResult =
        travelRecordService.getLatestFeed(
            null, null, null, null, null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3));

    assertThat(placeResult.items())
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .containsExactly(stationPublished.travelRecordId());
    assertThat(overlappingDateResult.items())
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .contains(beachPublished.travelRecordId(), stationPublished.travelRecordId());
    assertThat(outOfRangeDateResult.items()).isEmpty();
  }

  @Test
  @DisplayName("latest feed filters by region and city")
  void getLatestFeedFiltersByRegionAndCity() {
    User busanUser =
        userRepository.save(createUser("record-filter-busan@example.com", "record-filter-busan"));
    User jejuUser =
        userRepository.save(createUser("record-filter-jeju@example.com", "record-filter-jeju"));
    AuthenticatedUser busanAuthenticatedUser = AuthenticatedUser.from(busanUser);
    AuthenticatedUser jejuAuthenticatedUser = AuthenticatedUser.from(jejuUser);
    TravelRecordResDto busanDraft =
        createDraftWithOnePlace(
            busanAuthenticatedUser, "Busan Route", "Gwangalli Beach", "Busan Suyeong-gu");
    TravelRecordResDto jejuDraft =
        createDraftWithOnePlace(
            jejuAuthenticatedUser, "Jeju Route", "Seongsan Ilchulbong", "Jeju Seogwipo-si");
    TravelRecordResDto busanPublished =
        travelRecordService.publish(
            busanAuthenticatedUser, busanDraft.originalTravelId(), busanDraft.travelRecordId());
    TravelRecordResDto jejuPublished =
        travelRecordService.publish(
            jejuAuthenticatedUser, jejuDraft.originalTravelId(), jejuDraft.travelRecordId());

    TravelRecordFeedPageResDto regionResult =
        travelRecordService.getLatestFeed(null, null, null, null, null, null, null, "busan", null);
    TravelRecordFeedPageResDto cityResult =
        travelRecordService.getLatestFeed(
            null, null, null, null, null, null, null, null, "seogwipo");

    assertThat(regionResult.items())
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .contains(busanPublished.travelRecordId())
        .doesNotContain(jejuPublished.travelRecordId());
    assertThat(cityResult.items())
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .contains(jejuPublished.travelRecordId())
        .doesNotContain(busanPublished.travelRecordId());
  }

  @Test
  @DisplayName("latest feed search rejects invalid filters")
  void getLatestFeedRejectsInvalidFilters() {
    assertThatThrownBy(
            () ->
                travelRecordService.getLatestFeed(
                    null,
                    null,
                    null,
                    null,
                    null,
                    LocalDate.of(2026, 9, 3),
                    LocalDate.of(2026, 9, 1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel end date cannot be before travel start date.");
  }

  @Test
  @DisplayName("latest feed can be sorted by like count")
  void getLatestFeedSortsByLikeCount() {
    User lowLikeAuthor =
        userRepository.save(createUser("record-sort-like-low@example.com", "record-sort-like-low"));
    User highLikeAuthor =
        userRepository.save(
            createUser("record-sort-like-high@example.com", "record-sort-like-high"));
    User likerOne =
        userRepository.save(createUser("record-sort-like-one@example.com", "record-sort-like-one"));
    User likerTwo =
        userRepository.save(createUser("record-sort-like-two@example.com", "record-sort-like-two"));
    AuthenticatedUser lowAuthorUser = AuthenticatedUser.from(lowLikeAuthor);
    AuthenticatedUser highAuthorUser = AuthenticatedUser.from(highLikeAuthor);
    TravelRecordResDto lowDraft = createDraftWithOnePlace(lowAuthorUser, "Low Like");
    TravelRecordResDto highDraft = createDraftWithOnePlace(highAuthorUser, "High Like");
    TravelRecordResDto lowPublished =
        travelRecordService.publish(
            lowAuthorUser, lowDraft.originalTravelId(), lowDraft.travelRecordId());
    TravelRecordResDto highPublished =
        travelRecordService.publish(
            highAuthorUser, highDraft.originalTravelId(), highDraft.travelRecordId());
    travelRecordService.likeTravelRecord(
        AuthenticatedUser.from(likerOne), lowPublished.travelRecordId());
    travelRecordService.likeTravelRecord(
        AuthenticatedUser.from(likerOne), highPublished.travelRecordId());
    travelRecordService.likeTravelRecord(
        AuthenticatedUser.from(likerTwo), highPublished.travelRecordId());

    TravelRecordFeedPageResDto firstPage =
        travelRecordService.getLatestFeed(
            null, 1, null, null, null, null, null, TravelRecordFeedSort.MOST_LIKED);
    TravelRecordFeedPageResDto secondPage =
        travelRecordService.getLatestFeed(
            firstPage.nextCursor(),
            1,
            null,
            null,
            null,
            null,
            null,
            TravelRecordFeedSort.MOST_LIKED);

    assertThat(firstPage.items())
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .containsExactly(highPublished.travelRecordId());
    assertThat(firstPage.hasNext()).isTrue();
    assertThat(firstPage.nextCursor()).isNotBlank();
    assertThat(secondPage.items())
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .containsExactly(lowPublished.travelRecordId());
    assertThat(secondPage.hasNext()).isFalse();
  }

  @Test
  @DisplayName("latest feed can be sorted by view count")
  void getLatestFeedSortsByViewCount() {
    User lowViewAuthor =
        userRepository.save(createUser("record-sort-view-low@example.com", "record-sort-view-low"));
    User highViewAuthor =
        userRepository.save(
            createUser("record-sort-view-high@example.com", "record-sort-view-high"));
    AuthenticatedUser lowAuthorUser = AuthenticatedUser.from(lowViewAuthor);
    AuthenticatedUser highAuthorUser = AuthenticatedUser.from(highViewAuthor);
    TravelRecordResDto lowDraft = createDraftWithOnePlace(lowAuthorUser, "Low View");
    TravelRecordResDto highDraft = createDraftWithOnePlace(highAuthorUser, "High View");
    TravelRecordResDto lowPublished =
        travelRecordService.publish(
            lowAuthorUser, lowDraft.originalTravelId(), lowDraft.travelRecordId());
    TravelRecordResDto highPublished =
        travelRecordService.publish(
            highAuthorUser, highDraft.originalTravelId(), highDraft.travelRecordId());
    travelRecordService.getPublished(lowPublished.travelRecordId());
    travelRecordService.getPublished(highPublished.travelRecordId());
    travelRecordService.getPublished(highPublished.travelRecordId());

    TravelRecordFeedPageResDto result =
        travelRecordService.getLatestFeed(
            null, null, null, null, null, null, null, TravelRecordFeedSort.MOST_VIEWED);

    assertThat(result.items())
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .containsSubsequence(highPublished.travelRecordId(), lowPublished.travelRecordId());
    assertThat(result.items().getFirst().viewCount()).isGreaterThanOrEqualTo(2);
  }

  @Test
  @DisplayName("조회수 순 정렬도 커서로 다음 페이지를 이어서 조회한다")
  void getLatestFeedSortsByViewCountWithCursor() {
    User lowViewAuthor =
        userRepository.save(
            createUser("record-cursor-view-low@example.com", "record-cursor-view-low"));
    User highViewAuthor =
        userRepository.save(
            createUser("record-cursor-view-high@example.com", "record-cursor-view-high"));
    AuthenticatedUser lowAuthorUser = AuthenticatedUser.from(lowViewAuthor);
    AuthenticatedUser highAuthorUser = AuthenticatedUser.from(highViewAuthor);
    TravelRecordResDto lowDraft = createDraftWithOnePlace(lowAuthorUser, "Cursor Low View");
    TravelRecordResDto highDraft = createDraftWithOnePlace(highAuthorUser, "Cursor High View");
    TravelRecordResDto lowPublished =
        travelRecordService.publish(
            lowAuthorUser, lowDraft.originalTravelId(), lowDraft.travelRecordId());
    TravelRecordResDto highPublished =
        travelRecordService.publish(
            highAuthorUser, highDraft.originalTravelId(), highDraft.travelRecordId());
    travelRecordService.getPublished(lowPublished.travelRecordId());
    travelRecordService.getPublished(highPublished.travelRecordId());
    travelRecordService.getPublished(highPublished.travelRecordId());

    TravelRecordFeedPageResDto firstPage =
        travelRecordService.getLatestFeed(
            null, 1, null, null, null, null, null, TravelRecordFeedSort.MOST_VIEWED);
    TravelRecordFeedPageResDto secondPage =
        travelRecordService.getLatestFeed(
            firstPage.nextCursor(),
            1,
            null,
            null,
            null,
            null,
            null,
            TravelRecordFeedSort.MOST_VIEWED);

    assertThat(firstPage.items())
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .containsExactly(highPublished.travelRecordId());
    assertThat(firstPage.hasNext()).isTrue();
    assertThat(firstPage.nextCursor()).isNotBlank();
    assertThat(secondPage.items())
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .containsExactly(lowPublished.travelRecordId());
    assertThat(secondPage.hasNext()).isFalse();
  }

  @Test
  @DisplayName("provider와 providerPlaceId로도 장소 리뷰 요약을 집계한다")
  void getPlaceReviewSummaryByProviderAggregatesReviews() {
    User author =
        userRepository.save(createUser("summary-provider@example.com", "summary-provider"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(author);
    DraftWithPlanPlace reviewed =
        createDraftWithOneReviewedPlace(authenticatedUser, "Provider Summary", 4, "좋아요", null);
    travelRecordService.publish(
        authenticatedUser, reviewed.draft().originalTravelId(), reviewed.draft().travelRecordId());

    PlaceReviewSummaryResDto summary =
        travelRecordService.getPlaceReviewSummary(PlaceProvider.GOOGLE, "Busan Station");

    assertThat(summary.placeId()).isEqualTo("Busan Station");
    assertThat(summary.reviewCount()).isEqualTo(1);
    assertThat(summary.averageRating()).isEqualTo(4.0);
    assertThat(summary.ratingCounts()).containsEntry(4, 1L);
    assertThat(summary.reviews()).hasSize(1);
  }

  @Test
  @DisplayName("provider 기반 장소 리뷰 요약은 provider와 placeId를 모두 요구한다")
  void getPlaceReviewSummaryByProviderRejectsInvalidRequest() {
    assertThatThrownBy(() -> travelRecordService.getPlaceReviewSummary(null, "Busan Station"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Place provider is required.");
    assertThatThrownBy(() -> travelRecordService.getPlaceReviewSummary(PlaceProvider.GOOGLE, "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("latest feed rejects cursor when cursor sort does not match requested sort")
  void getLatestFeedRejectsMismatchedSortCursor() {
    User firstUser =
        userRepository.save(
            createUser("record-sort-cursor-first@example.com", "record-sort-cursor-first"));
    User secondUser =
        userRepository.save(
            createUser("record-sort-cursor-second@example.com", "record-sort-cursor-second"));
    AuthenticatedUser firstAuthenticatedUser = AuthenticatedUser.from(firstUser);
    AuthenticatedUser secondAuthenticatedUser = AuthenticatedUser.from(secondUser);
    TravelRecordResDto firstDraft =
        createDraftWithOnePlace(firstAuthenticatedUser, "First Cursor Sort");
    TravelRecordResDto secondDraft =
        createDraftWithOnePlace(secondAuthenticatedUser, "Second Cursor Sort");
    travelRecordService.publish(
        firstAuthenticatedUser, firstDraft.originalTravelId(), firstDraft.travelRecordId());
    travelRecordService.publish(
        secondAuthenticatedUser, secondDraft.originalTravelId(), secondDraft.travelRecordId());
    TravelRecordFeedPageResDto firstPage =
        travelRecordService.getLatestFeed(
            null, 1, null, null, null, null, null, TravelRecordFeedSort.MOST_LIKED);

    assertThatThrownBy(
            () ->
                travelRecordService.getLatestFeed(
                    firstPage.nextCursor(),
                    1,
                    null,
                    null,
                    null,
                    null,
                    null,
                    TravelRecordFeedSort.LATEST))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Feed cursor sort does not match requested sort.");
  }

  @Test
  @DisplayName("author can manage own travel records")
  void getMyRecordsReturnsOnlyAuthenticatedAuthorsRecords() {
    User owner = userRepository.save(createUser("record-my-owner@example.com", "record-my-owner"));
    User outsider =
        userRepository.save(createUser("record-my-outsider@example.com", "record-my-outsider"));
    AuthenticatedUser ownerUser = AuthenticatedUser.from(owner);
    AuthenticatedUser outsiderUser = AuthenticatedUser.from(outsider);
    TravelRecordResDto publishedDraft = createDraftWithOnePlace(ownerUser, "Published Mine");
    TravelRecordResDto draft = createDraftWithOnePlace(ownerUser, "Draft Mine");
    TravelRecordResDto outsiderDraft = createDraftWithOnePlace(outsiderUser, "Outsider Record");
    TravelRecordResDto published =
        travelRecordService.publish(
            ownerUser, publishedDraft.originalTravelId(), publishedDraft.travelRecordId());

    List<TravelRecordManageResDto> result = travelRecordService.getMyRecords(ownerUser);

    assertThat(result)
        .extracting(TravelRecordManageResDto::travelRecordId)
        .containsExactly(draft.travelRecordId(), published.travelRecordId());
    assertThat(result)
        .extracting(TravelRecordManageResDto::travelRecordId)
        .doesNotContain(outsiderDraft.travelRecordId());
    assertThat(result)
        .extracting(TravelRecordManageResDto::status)
        .containsExactly(TravelRecordStatus.DRAFT, TravelRecordStatus.PUBLISHED);
    assertThat(result.getFirst().title()).isEqualTo("Draft Mine");
    assertThat(result.getLast().publishedAt()).isNotNull();
    assertThat(result.getLast().createdAt()).isNotNull();
    assertThat(result.getLast().updatedAt()).isNotNull();
  }

  @Test
  @DisplayName("author can get own travel record detail regardless of status")
  void getMyRecordReturnsOwnRecordDetailRegardlessOfStatus() {
    User user = userRepository.save(createUser("record-my-detail@example.com", "record-my-detail"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Draft Detail");
    TravelRecordResDto publishedDraft =
        createDraftWithOnePlace(authenticatedUser, "Published Detail");
    TravelRecordResDto hiddenDraft = createDraftWithOnePlace(authenticatedUser, "Hidden Detail");
    TravelRecordResDto published =
        travelRecordService.publish(
            authenticatedUser, publishedDraft.originalTravelId(), publishedDraft.travelRecordId());
    travelRecordRepository.findById(hiddenDraft.travelRecordId()).orElseThrow().hide();

    TravelRecordResDto draftResult =
        travelRecordService.getMyRecord(authenticatedUser, draft.travelRecordId());
    TravelRecordResDto publishedResult =
        travelRecordService.getMyRecord(authenticatedUser, published.travelRecordId());
    TravelRecordResDto hiddenResult =
        travelRecordService.getMyRecord(authenticatedUser, hiddenDraft.travelRecordId());

    assertThat(draftResult.status()).isEqualTo(TravelRecordStatus.DRAFT);
    assertThat(draftResult.days()).hasSize(1);
    assertThat(publishedResult.status()).isEqualTo(TravelRecordStatus.PUBLISHED);
    assertThat(publishedResult.publishedAt()).isNotNull();
    assertThat(hiddenResult.status()).isEqualTo(TravelRecordStatus.HIDDEN);
    assertThat(hiddenResult.title()).isEqualTo("Hidden Detail");
  }

  @Test
  @DisplayName("non-author cannot get my travel record detail")
  void getMyRecordRejectsNonAuthor() {
    User owner =
        userRepository.save(
            createUser("record-my-detail-owner@example.com", "record-my-detail-owner"));
    User outsider =
        userRepository.save(
            createUser("record-my-detail-outsider@example.com", "record-my-detail-outsider"));
    AuthenticatedUser ownerUser = AuthenticatedUser.from(owner);
    TravelRecordResDto draft = createDraftWithOnePlace(ownerUser, "Owner Detail");

    assertThatThrownBy(
            () ->
                travelRecordService.getMyRecord(
                    AuthenticatedUser.from(outsider), draft.travelRecordId()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("User is not the travel record author.");
  }

  @Test
  @DisplayName("author can update own draft, published, and hidden travel records")
  void updateMyRecordUpdatesOwnRecordRegardlessOfStatus() {
    User user = userRepository.save(createUser("record-my-update@example.com", "record-my-update"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Draft Before");
    TravelRecordResDto publishedDraft =
        createDraftWithOnePlace(authenticatedUser, "Published Before");
    TravelRecordResDto hiddenDraft = createDraftWithOnePlace(authenticatedUser, "Hidden Before");
    TravelRecordResDto published =
        travelRecordService.publish(
            authenticatedUser, publishedDraft.originalTravelId(), publishedDraft.travelRecordId());
    travelRecordRepository.findById(hiddenDraft.travelRecordId()).orElseThrow().hide();

    TravelRecordResDto draftResult =
        travelRecordService.updateMyRecord(
            authenticatedUser,
            draft.travelRecordId(),
            new TravelRecordUpdateReqDto(
                "Draft After", "Draft content", "https://image.test/draft"));
    TravelRecordResDto publishedResult =
        travelRecordService.updateMyRecord(
            authenticatedUser,
            published.travelRecordId(),
            new TravelRecordUpdateReqDto(
                "Published After", "Published content", "https://image.test/published"));
    TravelRecordResDto hiddenResult =
        travelRecordService.updateMyRecord(
            authenticatedUser,
            hiddenDraft.travelRecordId(),
            new TravelRecordUpdateReqDto(
                "Hidden After", "Hidden content", "https://image.test/hidden"));

    assertThat(draftResult.status()).isEqualTo(TravelRecordStatus.DRAFT);
    assertThat(draftResult.title()).isEqualTo("Draft After");
    assertThat(draftResult.content()).isEqualTo("Draft content");
    assertThat(draftResult.coverImageUrl()).isEqualTo("https://image.test/draft");
    assertThat(publishedResult.status()).isEqualTo(TravelRecordStatus.PUBLISHED);
    assertThat(publishedResult.title()).isEqualTo("Published After");
    assertThat(publishedResult.publishedAt()).isNotNull();
    assertThat(hiddenResult.status()).isEqualTo(TravelRecordStatus.HIDDEN);
    assertThat(hiddenResult.title()).isEqualTo("Hidden After");
  }

  @Test
  @DisplayName("my record update keeps existing values when fields are null")
  void updateMyRecordKeepsExistingValuesForNullFields() {
    User user =
        userRepository.save(
            createUser("record-my-update-null@example.com", "record-my-update-null"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Keep Before");
    TravelRecordResDto updated =
        travelRecordService.updateMyRecord(
            authenticatedUser,
            draft.travelRecordId(),
            new TravelRecordUpdateReqDto("Keep Title", "Keep content", "https://image.test/keep"));

    TravelRecordResDto result =
        travelRecordService.updateMyRecord(
            authenticatedUser,
            updated.travelRecordId(),
            new TravelRecordUpdateReqDto(null, "Only content changed", null));

    assertThat(result.title()).isEqualTo("Keep Title");
    assertThat(result.content()).isEqualTo("Only content changed");
    assertThat(result.coverImageUrl()).isEqualTo("https://image.test/keep");
  }

  @Test
  @DisplayName("non-author cannot update my travel record")
  void updateMyRecordRejectsNonAuthor() {
    User owner =
        userRepository.save(
            createUser("record-my-update-owner@example.com", "record-my-update-owner"));
    User outsider =
        userRepository.save(
            createUser("record-my-update-outsider@example.com", "record-my-update-outsider"));
    AuthenticatedUser ownerUser = AuthenticatedUser.from(owner);
    TravelRecordResDto draft = createDraftWithOnePlace(ownerUser, "Owner Update");

    assertThatThrownBy(
            () ->
                travelRecordService.updateMyRecord(
                    AuthenticatedUser.from(outsider),
                    draft.travelRecordId(),
                    new TravelRecordUpdateReqDto("Hacked", null, null)))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("User is not the travel record author.");
  }

  @Test
  @DisplayName("my record update rejects blank title")
  void updateMyRecordRejectsBlankTitle() {
    User user =
        userRepository.save(
            createUser("record-my-update-blank@example.com", "record-my-update-blank"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Blank Title");

    assertThatThrownBy(
            () ->
                travelRecordService.updateMyRecord(
                    authenticatedUser,
                    draft.travelRecordId(),
                    new TravelRecordUpdateReqDto(" ", null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel record title cannot be blank.");
  }

  @Test
  @DisplayName("author can hide own published travel record")
  void hideMyRecordHidesPublishedRecord() {
    User user = userRepository.save(createUser("record-hide@example.com", "record-hide"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Hide Me");
    var place = draft.days().getFirst().places().getFirst();
    travelRecordService.createPlaceReview(
        authenticatedUser,
        draft.originalTravelId(),
        place.originalPlanPlaceId(),
        new PlaceReviewCreateReqDto(5, "Before hidden"));
    TravelRecordResDto published =
        travelRecordService.publish(
            authenticatedUser, draft.originalTravelId(), draft.travelRecordId());

    TravelRecordResDto result =
        travelRecordService.hideMyRecord(authenticatedUser, published.travelRecordId());

    assertThat(result.status()).isEqualTo(TravelRecordStatus.HIDDEN);
    assertThat(
            travelRecordService.getMyRecord(authenticatedUser, published.travelRecordId()).status())
        .isEqualTo(TravelRecordStatus.HIDDEN);
    assertThatThrownBy(() -> travelRecordService.getPublished(published.travelRecordId()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Travel record not found.");
    assertThat(travelRecordService.getLatestFeed(null, null).items())
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .doesNotContain(published.travelRecordId());
    assertThat(travelRecordService.getPlaceReviewSummary("Busan Station").reviews())
        .extracting(PlaceReviewSummaryResDto.PlaceReviewItemResDto::travelRecordId)
        .doesNotContain(published.travelRecordId());
  }

  @Test
  @DisplayName("hide my record is idempotent for already hidden record")
  void hideMyRecordIsIdempotent() {
    User user =
        userRepository.save(createUser("record-hide-again@example.com", "record-hide-again"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Hide Again");

    TravelRecordResDto firstResult =
        travelRecordService.hideMyRecord(authenticatedUser, draft.travelRecordId());
    TravelRecordResDto secondResult =
        travelRecordService.hideMyRecord(authenticatedUser, draft.travelRecordId());

    assertThat(firstResult.status()).isEqualTo(TravelRecordStatus.HIDDEN);
    assertThat(secondResult.status()).isEqualTo(TravelRecordStatus.HIDDEN);
    assertThat(secondResult.travelRecordId()).isEqualTo(draft.travelRecordId());
  }

  @Test
  @DisplayName("non-author cannot hide my travel record")
  void hideMyRecordRejectsNonAuthor() {
    User owner =
        userRepository.save(createUser("record-hide-owner@example.com", "record-hide-owner"));
    User outsider =
        userRepository.save(createUser("record-hide-outsider@example.com", "record-hide-outsider"));
    AuthenticatedUser ownerUser = AuthenticatedUser.from(owner);
    TravelRecordResDto draft = createDraftWithOnePlace(ownerUser, "Owner Hide");

    assertThatThrownBy(
            () ->
                travelRecordService.hideMyRecord(
                    AuthenticatedUser.from(outsider), draft.travelRecordId()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("User is not the travel record author.");
  }

  @Test
  @DisplayName("author can republish own hidden travel record")
  void republishMyRecordRepublishesHiddenPublishedRecord() {
    User user = userRepository.save(createUser("record-republish@example.com", "record-republish"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft =
        createDraftWithOneReviewedPlace(
                authenticatedUser, "Republish Me", 5, "Back again", List.of())
            .draft();
    TravelRecordResDto published =
        travelRecordService.publish(
            authenticatedUser, draft.originalTravelId(), draft.travelRecordId());
    TravelRecordResDto hidden =
        travelRecordService.hideMyRecord(authenticatedUser, published.travelRecordId());

    TravelRecordResDto result =
        travelRecordService.republishMyRecord(authenticatedUser, hidden.travelRecordId());

    assertThat(result.status()).isEqualTo(TravelRecordStatus.PUBLISHED);
    assertThat(result.publishedAt()).isEqualTo(published.publishedAt());
    assertThat(travelRecordService.getPublished(published.travelRecordId()).status())
        .isEqualTo(TravelRecordStatus.PUBLISHED);
    assertThat(travelRecordService.getLatestFeed(null, null).items())
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .contains(published.travelRecordId());
    assertThat(travelRecordService.getPlaceReviewSummary("Busan Station").reviews())
        .extracting(PlaceReviewSummaryResDto.PlaceReviewItemResDto::travelRecordId)
        .contains(published.travelRecordId());
  }

  @Test
  @DisplayName("draft hidden record cannot be republished")
  void republishMyRecordRejectsNeverPublishedHiddenRecord() {
    User user =
        userRepository.save(
            createUser("record-republish-draft@example.com", "record-republish-draft"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Draft Hidden");
    travelRecordService.hideMyRecord(authenticatedUser, draft.travelRecordId());

    assertThatThrownBy(
            () -> travelRecordService.republishMyRecord(authenticatedUser, draft.travelRecordId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Only previously published travel records can be republished.");
  }

  @Test
  @DisplayName("non-author cannot republish my travel record")
  void republishMyRecordRejectsNonAuthor() {
    User owner =
        userRepository.save(
            createUser("record-republish-owner@example.com", "record-republish-owner"));
    User outsider =
        userRepository.save(
            createUser("record-republish-outsider@example.com", "record-republish-outsider"));
    AuthenticatedUser ownerUser = AuthenticatedUser.from(owner);
    TravelRecordResDto draft = createDraftWithOnePlace(ownerUser, "Owner Republish");
    TravelRecordResDto published =
        travelRecordService.publish(ownerUser, draft.originalTravelId(), draft.travelRecordId());
    travelRecordService.hideMyRecord(ownerUser, published.travelRecordId());

    assertThatThrownBy(
            () ->
                travelRecordService.republishMyRecord(
                    AuthenticatedUser.from(outsider), published.travelRecordId()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("User is not the travel record author.");
  }

  @Test
  @DisplayName("user can bookmark a published travel record")
  void bookmarkTravelRecordSuccess() {
    User author =
        userRepository.save(
            createUser("record-bookmark-author@example.com", "record-bookmark-author"));
    User user =
        userRepository.save(createUser("record-bookmark-user@example.com", "record-bookmark-user"));
    AuthenticatedUser authorUser = AuthenticatedUser.from(author);
    TravelRecordResDto draft = createDraftWithOnePlace(authorUser, "Bookmark Target");
    TravelRecordResDto published =
        travelRecordService.publish(authorUser, draft.originalTravelId(), draft.travelRecordId());

    TravelRecordBookmarkResDto result =
        travelRecordService.bookmarkTravelRecord(
            AuthenticatedUser.from(user), published.travelRecordId());

    assertThat(result.bookmarkId()).isNotNull();
    assertThat(result.bookmarkedAt()).isNotNull();
    assertThat(result.travelRecord().travelRecordId()).isEqualTo(published.travelRecordId());
    assertThat(result.travelRecord().title()).isEqualTo("Bookmark Target");
  }

  @Test
  @DisplayName("bookmark rejects duplicated travel record bookmark")
  void bookmarkTravelRecordRejectsDuplicate() {
    User author =
        userRepository.save(
            createUser(
                "record-bookmark-duplicate-author@example.com",
                "record-bookmark-duplicate-author"));
    User user =
        userRepository.save(
            createUser(
                "record-bookmark-duplicate-user@example.com", "record-bookmark-duplicate-user"));
    AuthenticatedUser authorUser = AuthenticatedUser.from(author);
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authorUser, "Duplicate Bookmark");
    TravelRecordResDto published =
        travelRecordService.publish(authorUser, draft.originalTravelId(), draft.travelRecordId());
    travelRecordService.bookmarkTravelRecord(authenticatedUser, published.travelRecordId());

    assertThatThrownBy(
            () ->
                travelRecordService.bookmarkTravelRecord(
                    authenticatedUser, published.travelRecordId()))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("Travel record bookmark already exists.");
  }

  @Test
  @DisplayName("bookmark rejects non-published travel record")
  void bookmarkTravelRecordRejectsNonPublishedRecord() {
    User user =
        userRepository.save(
            createUser("record-bookmark-draft-user@example.com", "record-bookmark-draft-user"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Draft Bookmark");

    assertThatThrownBy(
            () ->
                travelRecordService.bookmarkTravelRecord(authenticatedUser, draft.travelRecordId()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Travel record not found.");
  }

  @Test
  @DisplayName("user can unbookmark a travel record idempotently")
  void unbookmarkTravelRecordSuccess() {
    User author =
        userRepository.save(
            createUser("record-unbookmark-author@example.com", "record-unbookmark-author"));
    User user =
        userRepository.save(
            createUser("record-unbookmark-user@example.com", "record-unbookmark-user"));
    AuthenticatedUser authorUser = AuthenticatedUser.from(author);
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authorUser, "Unbookmark Target");
    TravelRecordResDto published =
        travelRecordService.publish(authorUser, draft.originalTravelId(), draft.travelRecordId());
    travelRecordService.bookmarkTravelRecord(authenticatedUser, published.travelRecordId());

    travelRecordService.unbookmarkTravelRecord(authenticatedUser, published.travelRecordId());
    travelRecordService.unbookmarkTravelRecord(authenticatedUser, published.travelRecordId());

    assertThat(travelRecordService.getMyBookmarkedRecords(authenticatedUser)).isEmpty();
  }

  @Test
  @DisplayName("my bookmarked records returns only published records")
  void getMyBookmarkedRecordsReturnsOnlyPublishedRecords() {
    User author =
        userRepository.save(
            createUser("record-bookmark-list-author@example.com", "record-bookmark-list-author"));
    User user =
        userRepository.save(
            createUser("record-bookmark-list-user@example.com", "record-bookmark-list-user"));
    AuthenticatedUser authorUser = AuthenticatedUser.from(author);
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto firstDraft = createDraftWithOnePlace(authorUser, "First Bookmark");
    TravelRecordResDto secondDraft = createDraftWithOnePlace(authorUser, "Second Bookmark");
    TravelRecordResDto firstPublished =
        travelRecordService.publish(
            authorUser, firstDraft.originalTravelId(), firstDraft.travelRecordId());
    TravelRecordResDto secondPublished =
        travelRecordService.publish(
            authorUser, secondDraft.originalTravelId(), secondDraft.travelRecordId());
    travelRecordService.bookmarkTravelRecord(authenticatedUser, firstPublished.travelRecordId());
    travelRecordService.bookmarkTravelRecord(authenticatedUser, secondPublished.travelRecordId());
    travelRecordService.hideMyRecord(authorUser, firstPublished.travelRecordId());

    List<TravelRecordBookmarkResDto> result =
        travelRecordService.getMyBookmarkedRecords(authenticatedUser);

    assertThat(result)
        .extracting(bookmark -> bookmark.travelRecord().travelRecordId())
        .containsExactly(secondPublished.travelRecordId());
    assertThat(result.getFirst().travelRecord().title()).isEqualTo("Second Bookmark");
  }

  @Test
  @DisplayName("published travel record detail increases view count")
  void getPublishedIncreasesViewCount() {
    User author =
        userRepository.save(createUser("record-view-author@example.com", "record-view-author"));
    AuthenticatedUser authorUser = AuthenticatedUser.from(author);
    TravelRecordResDto draft = createDraftWithOnePlace(authorUser, "View Target");
    TravelRecordResDto published =
        travelRecordService.publish(authorUser, draft.originalTravelId(), draft.travelRecordId());

    TravelRecordResDto firstResult = travelRecordService.getPublished(published.travelRecordId());
    TravelRecordResDto secondResult = travelRecordService.getPublished(published.travelRecordId());

    assertThat(firstResult.viewCount()).isEqualTo(1);
    assertThat(secondResult.viewCount()).isEqualTo(2);
    assertThat(secondResult.likeCount()).isZero();
    assertThat(travelRecordService.getLatestFeed(null, null).items())
        .filteredOn(item -> item.travelRecordId().equals(published.travelRecordId()))
        .extracting(TravelRecordFeedResDto::viewCount)
        .containsExactly(2L);
  }

  @Test
  @DisplayName("user can like and unlike a published travel record")
  void likeAndUnlikeTravelRecordSuccess() {
    User author =
        userRepository.save(createUser("record-like-author@example.com", "record-like-author"));
    User user = userRepository.save(createUser("record-like-user@example.com", "record-like-user"));
    AuthenticatedUser authorUser = AuthenticatedUser.from(author);
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authorUser, "Like Target");
    TravelRecordResDto published =
        travelRecordService.publish(authorUser, draft.originalTravelId(), draft.travelRecordId());

    TravelRecordLikeResDto like =
        travelRecordService.likeTravelRecord(authenticatedUser, published.travelRecordId());

    assertThat(like.likeId()).isNotNull();
    assertThat(like.likedAt()).isNotNull();
    assertThat(like.travelRecordId()).isEqualTo(published.travelRecordId());
    assertThat(like.likeCount()).isEqualTo(1);
    assertThat(travelRecordService.getMyRecord(authorUser, published.travelRecordId()).likeCount())
        .isEqualTo(1);

    travelRecordService.unlikeTravelRecord(authenticatedUser, published.travelRecordId());
    travelRecordService.unlikeTravelRecord(authenticatedUser, published.travelRecordId());

    assertThat(travelRecordService.getMyRecord(authorUser, published.travelRecordId()).likeCount())
        .isZero();
  }

  @Test
  @DisplayName("feed marks records liked by authenticated user")
  void getLatestFeedIncludesLikedByMe() {
    User firstAuthor =
        userRepository.save(
            createUser("record-feed-liked-first-author@example.com", "feed-liked-first-author"));
    User secondAuthor =
        userRepository.save(
            createUser("record-feed-liked-second-author@example.com", "feed-liked-second-author"));
    User viewer =
        userRepository.save(
            createUser("record-feed-liked-viewer@example.com", "feed-liked-viewer"));
    AuthenticatedUser firstAuthorUser = AuthenticatedUser.from(firstAuthor);
    AuthenticatedUser secondAuthorUser = AuthenticatedUser.from(secondAuthor);
    AuthenticatedUser viewerUser = AuthenticatedUser.from(viewer);
    TravelRecordResDto firstDraft = createDraftWithOnePlace(firstAuthorUser, "Liked Feed");
    TravelRecordResDto secondDraft = createDraftWithOnePlace(secondAuthorUser, "Not Liked Feed");
    TravelRecordResDto firstPublished =
        travelRecordService.publish(
            firstAuthorUser, firstDraft.originalTravelId(), firstDraft.travelRecordId());
    TravelRecordResDto secondPublished =
        travelRecordService.publish(
            secondAuthorUser, secondDraft.originalTravelId(), secondDraft.travelRecordId());
    travelRecordService.likeTravelRecord(viewerUser, firstPublished.travelRecordId());

    TravelRecordFeedPageResDto authenticatedFeed =
        travelRecordService.getLatestFeed(
            viewerUser, null, null, null, null, null, null, null, null, null);
    TravelRecordFeedPageResDto anonymousFeed = travelRecordService.getLatestFeed(null, null);

    assertThat(authenticatedFeed.items())
        .filteredOn(item -> item.travelRecordId().equals(firstPublished.travelRecordId()))
        .extracting(TravelRecordFeedResDto::likedByMe)
        .containsExactly(true);
    assertThat(authenticatedFeed.items())
        .filteredOn(item -> item.travelRecordId().equals(secondPublished.travelRecordId()))
        .extracting(TravelRecordFeedResDto::likedByMe)
        .containsExactly(false);
    assertThat(anonymousFeed.items())
        .filteredOn(item -> item.travelRecordId().equals(firstPublished.travelRecordId()))
        .extracting(TravelRecordFeedResDto::likedByMe)
        .containsExactly(false);
  }

  @Test
  @DisplayName("like rejects duplicated travel record like")
  void likeTravelRecordRejectsDuplicate() {
    User author =
        userRepository.save(
            createUser("record-like-duplicate-author@example.com", "record-like-duplicate-author"));
    User user =
        userRepository.save(
            createUser("record-like-duplicate-user@example.com", "record-like-duplicate-user"));
    AuthenticatedUser authorUser = AuthenticatedUser.from(author);
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authorUser, "Duplicate Like");
    TravelRecordResDto published =
        travelRecordService.publish(authorUser, draft.originalTravelId(), draft.travelRecordId());
    travelRecordService.likeTravelRecord(authenticatedUser, published.travelRecordId());

    assertThatThrownBy(
            () ->
                travelRecordService.likeTravelRecord(authenticatedUser, published.travelRecordId()))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("Travel record like already exists.");
  }

  @Test
  @DisplayName("like rejects non-published travel record")
  void likeTravelRecordRejectsNonPublishedRecord() {
    User user =
        userRepository.save(
            createUser("record-like-draft-user@example.com", "record-like-draft-user"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Draft Like");

    assertThatThrownBy(
            () -> travelRecordService.likeTravelRecord(authenticatedUser, draft.travelRecordId()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Travel record not found.");
  }

  @Test
  @DisplayName("user can create and list comments for a published travel record")
  void createAndGetCommentsSuccess() {
    User author =
        userRepository.save(
            createUser("record-comment-author@example.com", "record-comment-author"));
    User commenter =
        userRepository.save(createUser("record-comment-user@example.com", "record-comment-user"));
    AuthenticatedUser authorUser = AuthenticatedUser.from(author);
    AuthenticatedUser commenterUser = AuthenticatedUser.from(commenter);
    TravelRecordResDto draft = createDraftWithOnePlace(authorUser, "Comment Target");
    TravelRecordResDto published =
        travelRecordService.publish(authorUser, draft.originalTravelId(), draft.travelRecordId());

    TravelRecordCommentResDto created =
        travelRecordService.createComment(
            commenterUser,
            published.travelRecordId(),
            new TravelRecordCommentCreateReqDto("  Great route!  "));

    assertThat(created.commentId()).isNotNull();
    assertThat(created.travelRecordId()).isEqualTo(published.travelRecordId());
    assertThat(created.authorId()).isEqualTo(commenter.getId());
    assertThat(created.authorNickname()).isEqualTo(commenter.getNickname());
    assertThat(created.content()).isEqualTo("Great route!");

    List<TravelRecordCommentResDto> comments =
        travelRecordService.getComments(published.travelRecordId());

    assertThat(comments)
        .extracting(TravelRecordCommentResDto::commentId)
        .containsExactly(created.commentId());
  }

  @Test
  @DisplayName("comment rejects non-published travel record")
  void createCommentRejectsNonPublishedRecord() {
    User user =
        userRepository.save(
            createUser("record-comment-draft-user@example.com", "record-comment-draft-user"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Draft Comment");

    assertThatThrownBy(
            () ->
                travelRecordService.createComment(
                    authenticatedUser,
                    draft.travelRecordId(),
                    new TravelRecordCommentCreateReqDto("Cannot comment")))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Travel record not found.");
  }

  @Test
  @DisplayName("comment content cannot be blank")
  void createCommentRejectsBlankContent() {
    User author =
        userRepository.save(
            createUser("record-comment-blank-author@example.com", "record-comment-blank-author"));
    AuthenticatedUser authorUser = AuthenticatedUser.from(author);
    TravelRecordResDto draft = createDraftWithOnePlace(authorUser, "Blank Comment");
    TravelRecordResDto published =
        travelRecordService.publish(authorUser, draft.originalTravelId(), draft.travelRecordId());

    assertThatThrownBy(
            () ->
                travelRecordService.createComment(
                    authorUser,
                    published.travelRecordId(),
                    new TravelRecordCommentCreateReqDto("   ")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel record comment content is required.");
  }

  @Test
  @DisplayName("comment author can update and delete comment")
  void updateAndDeleteCommentSuccess() {
    User author =
        userRepository.save(
            createUser("record-comment-update-author@example.com", "record-comment-update-author"));
    User commenter =
        userRepository.save(
            createUser("record-comment-update-user@example.com", "record-comment-update-user"));
    AuthenticatedUser authorUser = AuthenticatedUser.from(author);
    AuthenticatedUser commenterUser = AuthenticatedUser.from(commenter);
    TravelRecordResDto draft = createDraftWithOnePlace(authorUser, "Update Comment");
    TravelRecordResDto published =
        travelRecordService.publish(authorUser, draft.originalTravelId(), draft.travelRecordId());
    TravelRecordCommentResDto created =
        travelRecordService.createComment(
            commenterUser,
            published.travelRecordId(),
            new TravelRecordCommentCreateReqDto("Before"));

    TravelRecordCommentResDto updated =
        travelRecordService.updateComment(
            commenterUser,
            published.travelRecordId(),
            created.commentId(),
            new TravelRecordCommentUpdateReqDto("After"));

    assertThat(updated.commentId()).isEqualTo(created.commentId());
    assertThat(updated.content()).isEqualTo("After");

    travelRecordService.deleteComment(
        commenterUser, published.travelRecordId(), created.commentId());

    assertThat(travelRecordService.getComments(published.travelRecordId())).isEmpty();
  }

  @Test
  @DisplayName("comment cannot be updated by another user")
  void updateCommentRejectsNonAuthor() {
    User author =
        userRepository.save(
            createUser(
                "record-comment-forbidden-author@example.com", "record-comment-forbidden-author"));
    User commenter =
        userRepository.save(
            createUser(
                "record-comment-forbidden-user@example.com", "record-comment-forbidden-user"));
    User outsider =
        userRepository.save(
            createUser(
                "record-comment-forbidden-outsider@example.com",
                "record-comment-forbidden-outsider"));
    AuthenticatedUser authorUser = AuthenticatedUser.from(author);
    AuthenticatedUser commenterUser = AuthenticatedUser.from(commenter);
    AuthenticatedUser outsiderUser = AuthenticatedUser.from(outsider);
    TravelRecordResDto draft = createDraftWithOnePlace(authorUser, "Forbidden Comment");
    TravelRecordResDto published =
        travelRecordService.publish(authorUser, draft.originalTravelId(), draft.travelRecordId());
    TravelRecordCommentResDto created =
        travelRecordService.createComment(
            commenterUser,
            published.travelRecordId(),
            new TravelRecordCommentCreateReqDto("Owner only"));

    assertThatThrownBy(
            () ->
                travelRecordService.updateComment(
                    outsiderUser,
                    published.travelRecordId(),
                    created.commentId(),
                    new TravelRecordCommentUpdateReqDto("Nope")))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("User is not the travel record comment author.");
  }

  @Test
  @DisplayName("travel member can create place review for a plan place")
  void createPlaceReviewSuccess() {
    User user = userRepository.save(createUser("review-owner@example.com", "review-owner"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);
    var place = draft.days().getFirst().places().getFirst();

    PlaceReviewResDto result =
        travelRecordService.createPlaceReview(
            authenticatedUser,
            draft.originalTravelId(),
            place.originalPlanPlaceId(),
            new PlaceReviewCreateReqDto(
                5,
                "Best place in this route",
                List.of("  night  ", "return", "night", ""),
                90,
                List.of(" uploads/images/place.jpg ", "uploads/videos/place.mp4")));

    assertThat(result.planPlaceId()).isEqualTo(place.originalPlanPlaceId());
    assertThat(result.travelRecordPlaceId()).isNull();
    assertThat(result.rating()).isEqualTo(5);
    assertThat(result.stayMinutes()).isEqualTo(90);
    assertThat(result.content()).isEqualTo("Best place in this route");
    assertThat(result.tags()).containsExactly("night", "return");
    assertThat(result.mediaUrls())
        .containsExactly(
            "https://signed.example.com/uploads/images/place.jpg",
            "https://signed.example.com/uploads/videos/place.mp4");
    assertThat(
            placeReviewRepository.findByPlanPlace_IdAndAuthor_Id(
                place.originalPlanPlaceId(), user.getId()))
        .isPresent();
  }

  @Test
  @DisplayName("travel member can create place review before travel is completed")
  void createPlaceReviewBeforeTravelCompleted() {
    User user =
        userRepository.save(createUser("review-before-completed@example.com", "review-before"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
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
    PlanResDto firstDay =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    PlanPlaceResDto place =
        createPlace(authenticatedUser, firstDay.planId(), 1, "Busan Station", "Busan");

    PlaceReviewResDto result =
        travelRecordService.createPlaceReview(
            authenticatedUser,
            travel.id(),
            place.planPlaceId(),
            new PlaceReviewCreateReqDto(5, "Already impressive"));

    assertThat(result.planPlaceId()).isEqualTo(place.planPlaceId());
    assertThat(result.rating()).isEqualTo(5);
    assertThat(
            placeReviewRepository.findByPlanPlace_IdAndAuthor_Id(place.planPlaceId(), user.getId()))
        .isPresent();
  }

  @Test
  @DisplayName("place review cannot be created twice for the same plan place")
  void createPlaceReviewRejectsDuplicate() {
    User user = userRepository.save(createUser("review-duplicate@example.com", "review-duplicate"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);
    var place = draft.days().getFirst().places().getFirst();
    travelRecordService.createPlaceReview(
        authenticatedUser,
        draft.originalTravelId(),
        place.originalPlanPlaceId(),
        new PlaceReviewCreateReqDto(4, "Good"));

    assertThatThrownBy(
            () ->
                travelRecordService.createPlaceReview(
                    authenticatedUser,
                    draft.originalTravelId(),
                    place.originalPlanPlaceId(),
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
                    place.originalPlanPlaceId(),
                    new PlaceReviewCreateReqDto(6, "Too high")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Place review rating must be between 1 and 5.");
  }

  @Test
  @DisplayName("travel member can get place review for a plan place")
  void getPlaceReviewSuccess() {
    User user = userRepository.save(createUser("review-get@example.com", "review-get"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);
    var place = draft.days().getFirst().places().getFirst();
    PlaceReviewResDto created =
        travelRecordService.createPlaceReview(
            authenticatedUser,
            draft.originalTravelId(),
            place.originalPlanPlaceId(),
            new PlaceReviewCreateReqDto(5, "Worth visiting", List.of("맛집", "재방문")));

    PlaceReviewResDto result =
        travelRecordService.getPlaceReview(
            authenticatedUser, draft.originalTravelId(), place.originalPlanPlaceId());

    assertThat(result.placeReviewId()).isEqualTo(created.placeReviewId());
    assertThat(result.planPlaceId()).isEqualTo(place.originalPlanPlaceId());
    assertThat(result.travelRecordPlaceId()).isNull();
    assertThat(result.rating()).isEqualTo(5);
    assertThat(result.content()).isEqualTo("Worth visiting");
    assertThat(result.tags()).containsExactly("맛집", "재방문");
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
                    authenticatedUser, draft.originalTravelId(), place.originalPlanPlaceId()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Place review not found.");
  }

  @Test
  @DisplayName("place review summary aggregates only published travel record reviews")
  void getPlaceReviewSummaryAggregatesPublishedReviews() {
    User firstUser =
        userRepository.save(createUser("review-summary-first@example.com", "review-summary-first"));
    User secondUser =
        userRepository.save(
            createUser("review-summary-second@example.com", "review-summary-second"));
    User draftUser =
        userRepository.save(createUser("review-summary-draft@example.com", "review-summary-draft"));
    AuthenticatedUser firstAuthenticatedUser = AuthenticatedUser.from(firstUser);
    AuthenticatedUser secondAuthenticatedUser = AuthenticatedUser.from(secondUser);
    AuthenticatedUser draftAuthenticatedUser = AuthenticatedUser.from(draftUser);
    TravelRecordResDto firstDraft =
        createDraftWithOneReviewedPlace(
                firstAuthenticatedUser, "First Record", 4, "Good route", List.of("야경", "가족"))
            .draft();
    TravelRecordResDto secondDraft =
        createDraftWithOneReviewedPlace(
                secondAuthenticatedUser, "Second Record", 5, "Perfect stop", List.of("맛집"))
            .draft();
    TravelRecordResDto hiddenDraft =
        createDraftWithOneReviewedPlace(
                draftAuthenticatedUser, "Draft Record", 1, "Still draft", List.of())
            .draft();
    travelRecordService.publish(
        firstAuthenticatedUser, firstDraft.originalTravelId(), firstDraft.travelRecordId());
    travelRecordService.publish(
        secondAuthenticatedUser, secondDraft.originalTravelId(), secondDraft.travelRecordId());

    PlaceReviewSummaryResDto result = travelRecordService.getPlaceReviewSummary("Busan Station");

    assertThat(result.placeId()).isEqualTo("Busan Station");
    assertThat(result.reviewCount()).isEqualTo(2);
    assertThat(result.averageRating()).isEqualTo(4.5);
    assertThat(result.ratingCounts()).containsEntry(1, 0L);
    assertThat(result.ratingCounts()).containsEntry(4, 1L);
    assertThat(result.ratingCounts()).containsEntry(5, 1L);
    assertThat(result.reviews())
        .extracting(PlaceReviewSummaryResDto.PlaceReviewItemResDto::content)
        .containsExactlyInAnyOrder("Perfect stop", "Good route");
    assertThat(result.reviews())
        .flatExtracting(PlaceReviewSummaryResDto.PlaceReviewItemResDto::tags)
        .contains("야경", "가족", "맛집");
    assertThat(result.reviews())
        .extracting(PlaceReviewSummaryResDto.PlaceReviewItemResDto::travelRecordId)
        .doesNotContain(hiddenDraft.travelRecordId());
  }

  @Test
  @DisplayName("place review summary returns empty aggregate when there are no public reviews")
  void getPlaceReviewSummaryReturnsEmptyAggregate() {
    PlaceReviewSummaryResDto result = travelRecordService.getPlaceReviewSummary("missing-place");

    assertThat(result.reviewCount()).isZero();
    assertThat(result.averageRating()).isEqualTo(0.0);
    assertThat(result.ratingCounts()).containsEntry(1, 0L);
    assertThat(result.ratingCounts()).containsEntry(5, 0L);
    assertThat(result.reviews()).isEmpty();
  }

  @Test
  @DisplayName("place travel records returns published records containing the place")
  void getTravelRecordsByPlaceReturnsPublishedRecordsContainingPlace() {
    User firstUser =
        userRepository.save(createUser("record-place-first@example.com", "record-place-first"));
    User secondUser =
        userRepository.save(createUser("record-place-second@example.com", "record-place-second"));
    User hiddenUser =
        userRepository.save(createUser("record-place-hidden@example.com", "record-place-hidden"));
    User otherUser =
        userRepository.save(createUser("record-place-other@example.com", "record-place-other"));
    AuthenticatedUser firstAuthenticatedUser = AuthenticatedUser.from(firstUser);
    AuthenticatedUser secondAuthenticatedUser = AuthenticatedUser.from(secondUser);
    AuthenticatedUser hiddenAuthenticatedUser = AuthenticatedUser.from(hiddenUser);
    AuthenticatedUser otherAuthenticatedUser = AuthenticatedUser.from(otherUser);
    TravelRecordResDto firstDraft =
        createDraftWithOnePlace(firstAuthenticatedUser, "First Place Record");
    TravelRecordResDto secondDraft =
        createDraftWithOnePlace(secondAuthenticatedUser, "Second Place Record");
    TravelRecordResDto hiddenDraft =
        createDraftWithOnePlace(hiddenAuthenticatedUser, "Hidden Place Record");
    TravelRecordResDto otherDraft =
        createDraftWithOnePlace(otherAuthenticatedUser, "Other Place Record", "Haeundae");
    TravelRecordResDto firstPublished =
        travelRecordService.publish(
            firstAuthenticatedUser, firstDraft.originalTravelId(), firstDraft.travelRecordId());
    TravelRecordResDto secondPublished =
        travelRecordService.publish(
            secondAuthenticatedUser, secondDraft.originalTravelId(), secondDraft.travelRecordId());
    TravelRecordResDto hiddenPublished =
        travelRecordService.publish(
            hiddenAuthenticatedUser, hiddenDraft.originalTravelId(), hiddenDraft.travelRecordId());
    travelRecordService.publish(
        otherAuthenticatedUser, otherDraft.originalTravelId(), otherDraft.travelRecordId());
    travelRecordService.hideMyRecord(hiddenAuthenticatedUser, hiddenPublished.travelRecordId());

    List<TravelRecordFeedResDto> result =
        travelRecordService
            .getTravelRecordsByPlace((AuthenticatedUser) null, "Busan Station", null, null)
            .items();

    assertThat(result)
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .containsExactly(secondPublished.travelRecordId(), firstPublished.travelRecordId());
    assertThat(result)
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .doesNotContain(hiddenPublished.travelRecordId(), otherDraft.travelRecordId());
    assertThat(result.getFirst().title()).isEqualTo("Second Place Record");
    assertThat(result.getFirst().likeCount()).isZero();
    assertThat(result.getFirst().viewCount()).isZero();
  }

  @Test
  @DisplayName("place travel records returns empty list when there are no public records")
  void getTravelRecordsByPlaceReturnsEmptyList() {
    List<TravelRecordFeedResDto> result =
        travelRecordService
            .getTravelRecordsByPlace((AuthenticatedUser) null, "missing-place", null, null)
            .items();

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("place travel records supports cursor pagination")
  void getTravelRecordsByPlaceSupportsCursorPagination() {
    User firstUser =
        userRepository.save(
            createUser("record-place-page-first@example.com", "record-place-page-first"));
    User secondUser =
        userRepository.save(
            createUser("record-place-page-second@example.com", "record-place-page-second"));
    AuthenticatedUser firstAuthenticatedUser = AuthenticatedUser.from(firstUser);
    AuthenticatedUser secondAuthenticatedUser = AuthenticatedUser.from(secondUser);
    TravelRecordResDto firstDraft =
        createDraftWithOnePlace(firstAuthenticatedUser, "First Place Page");
    TravelRecordResDto secondDraft =
        createDraftWithOnePlace(secondAuthenticatedUser, "Second Place Page");
    TravelRecordResDto firstPublished =
        travelRecordService.publish(
            firstAuthenticatedUser, firstDraft.originalTravelId(), firstDraft.travelRecordId());
    TravelRecordResDto secondPublished =
        travelRecordService.publish(
            secondAuthenticatedUser, secondDraft.originalTravelId(), secondDraft.travelRecordId());

    TravelRecordFeedPageResDto firstPage =
        travelRecordService.getTravelRecordsByPlace(
            (AuthenticatedUser) null, "Busan Station", null, 1);
    TravelRecordFeedPageResDto secondPage =
        travelRecordService.getTravelRecordsByPlace(
            (AuthenticatedUser) null, "Busan Station", firstPage.nextCursor(), 1);

    assertThat(firstPage.items())
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .containsExactly(secondPublished.travelRecordId());
    assertThat(firstPage.hasNext()).isTrue();
    assertThat(firstPage.nextCursor()).isNotBlank();
    assertThat(secondPage.items())
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .containsExactly(firstPublished.travelRecordId());
    assertThat(secondPage.hasNext()).isFalse();
    assertThat(secondPage.nextCursor()).isNull();
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
        place.originalPlanPlaceId(),
        new PlaceReviewCreateReqDto(3, "Before"));

    PlaceReviewResDto result =
        travelRecordService.updatePlaceReview(
            authenticatedUser,
            draft.originalTravelId(),
            place.originalPlanPlaceId(),
            new PlaceReviewUpdateReqDto(5, "After", List.of("  야경", "카페", "야경")));

    assertThat(result.rating()).isEqualTo(5);
    assertThat(result.content()).isEqualTo("After");
    assertThat(result.tags()).containsExactly("야경", "카페");
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
        place.originalPlanPlaceId(),
        new PlaceReviewCreateReqDto(4, "Before", List.of("기존")));

    PlaceReviewResDto result =
        travelRecordService.updatePlaceReview(
            authenticatedUser,
            draft.originalTravelId(),
            place.originalPlanPlaceId(),
            new PlaceReviewUpdateReqDto(null, "Only content changed"));

    assertThat(result.rating()).isEqualTo(4);
    assertThat(result.content()).isEqualTo("Only content changed");
    assertThat(result.tags()).containsExactly("기존");
  }

  @Test
  @DisplayName("place review update can clear tags with empty list")
  void updatePlaceReviewCanClearTags() {
    User user =
        userRepository.save(createUser("review-clear-tags@example.com", "review-clear-tags"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);
    var place = draft.days().getFirst().places().getFirst();
    travelRecordService.createPlaceReview(
        authenticatedUser,
        draft.originalTravelId(),
        place.originalPlanPlaceId(),
        new PlaceReviewCreateReqDto(4, "Before", List.of("야경", "가족")));

    PlaceReviewResDto result =
        travelRecordService.updatePlaceReview(
            authenticatedUser,
            draft.originalTravelId(),
            place.originalPlanPlaceId(),
            new PlaceReviewUpdateReqDto(null, null, List.of()));

    assertThat(result.rating()).isEqualTo(4);
    assertThat(result.content()).isEqualTo("Before");
    assertThat(result.tags()).isEmpty();
  }

  @Test
  @DisplayName("place review tags must be 10 or fewer")
  void createPlaceReviewRejectsTooManyTags() {
    User user =
        userRepository.save(createUser("review-too-many-tags@example.com", "review-too-many-tags"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);
    var place = draft.days().getFirst().places().getFirst();

    assertThatThrownBy(
            () ->
                travelRecordService.createPlaceReview(
                    authenticatedUser,
                    draft.originalTravelId(),
                    place.originalPlanPlaceId(),
                    new PlaceReviewCreateReqDto(
                        5,
                        "Too many tags",
                        List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Place review tags must be 10 or fewer.");
  }

  @Test
  @DisplayName("place review update returns not found when review does not exist")
  void updatePlaceReviewNotFound() {
    User user =
        userRepository.save(
            createUser("review-update-missing@example.com", "review-update-missing"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);
    var place = draft.days().getFirst().places().getFirst();

    assertThatThrownBy(
            () ->
                travelRecordService.updatePlaceReview(
                    authenticatedUser,
                    draft.originalTravelId(),
                    place.originalPlanPlaceId(),
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
        place.originalPlanPlaceId(),
        new PlaceReviewCreateReqDto(4, "Before"));

    assertThatThrownBy(
            () ->
                travelRecordService.updatePlaceReview(
                    authenticatedUser,
                    draft.originalTravelId(),
                    place.originalPlanPlaceId(),
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
        place.originalPlanPlaceId(),
        new PlaceReviewCreateReqDto(5, "Delete me"));

    travelRecordService.deletePlaceReview(
        authenticatedUser, draft.originalTravelId(), place.originalPlanPlaceId());

    assertThat(
            placeReviewRepository.findByPlanPlace_IdAndAuthor_Id(
                place.originalPlanPlaceId(), user.getId()))
        .isEmpty();
    assertThatThrownBy(
            () ->
                travelRecordService.getPlaceReview(
                    authenticatedUser, draft.originalTravelId(), place.originalPlanPlaceId()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Place review not found.");
  }

  @Test
  @DisplayName("place review delete returns not found when review does not exist")
  void deletePlaceReviewNotFound() {
    User user =
        userRepository.save(
            createUser("review-delete-missing@example.com", "review-delete-missing"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser);
    var place = draft.days().getFirst().places().getFirst();

    assertThatThrownBy(
            () ->
                travelRecordService.deletePlaceReview(
                    authenticatedUser, draft.originalTravelId(), place.originalPlanPlaceId()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Place review not found.");
  }

  @Test
  @DisplayName("존재하지 않는 리소스를 가리키면 ResourceNotFoundException을 던진다")
  void rejectsMissingResources() {
    User user = userRepository.save(createUser("nf@example.com", "nf"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    java.util.UUID unknown = java.util.UUID.randomUUID();
    TravelResDto travel = createCompletedTravel(authenticatedUser);

    assertThatThrownBy(
            () ->
                travelRecordService.createDraft(
                    authenticatedUser, unknown, new TravelRecordCreateReqDto("t", null, null, 5)))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Travel not found.");
    assertThatThrownBy(() -> travelRecordService.getDraft(authenticatedUser, travel.id(), unknown))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Travel record not found.");
    assertThatThrownBy(() -> travelRecordService.getMyRecord(authenticatedUser, unknown))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Travel record not found.");
    assertThatThrownBy(() -> travelRecordService.getPublished(unknown))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Travel record not found.");
    assertThatThrownBy(
            () ->
                travelRecordService.createPlaceReview(
                    authenticatedUser,
                    travel.id(),
                    unknown,
                    new PlaceReviewCreateReqDto(5, "good")))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Plan place not found.");
    assertThatThrownBy(
            () -> travelRecordService.getPlaceReview(authenticatedUser, travel.id(), unknown))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(
            () ->
                travelRecordService.updateComment(
                    authenticatedUser,
                    unknown,
                    unknown,
                    new TravelRecordCommentUpdateReqDto("edited")))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  @DisplayName("인증 정보가 없거나 사용자를 찾을 수 없으면 UnauthenticatedException을 던진다")
  void rejectsUnauthenticatedUser() {
    java.util.UUID unknown = java.util.UUID.randomUUID();
    AuthenticatedUser unknownUser =
        new AuthenticatedUser(unknown, "ghost@example.com", "ghost", List.of());

    assertThatThrownBy(() -> travelRecordService.getMyRecords(null))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(
            () ->
                travelRecordService.getMyRecords(
                    new AuthenticatedUser(null, "a@example.com", "a", List.of())))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> travelRecordService.getMyRecords(unknownUser))
        .isInstanceOf(UnauthenticatedException.class);
  }

  @Test
  @DisplayName("초안 생성·수정 요청의 제목이 공백이거나 평점이 범위를 벗어나면 거부한다")
  void rejectsInvalidDraftRequests() {
    User user = userRepository.save(createUser("draft-invalid@example.com", "draft-invalid"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelResDto travel = createCompletedTravel(authenticatedUser);

    assertThatThrownBy(
            () ->
                travelRecordService.createDraft(
                    authenticatedUser,
                    travel.id(),
                    new TravelRecordCreateReqDto("   ", null, null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel record title cannot be blank.");
    assertThatThrownBy(
            () ->
                travelRecordService.createDraft(
                    authenticatedUser,
                    travel.id(),
                    new TravelRecordCreateReqDto("title", null, null, 6)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel record overall rating must be between 1 and 5.");

    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Valid");
    assertThatThrownBy(
            () ->
                travelRecordService.updateDraft(
                    authenticatedUser,
                    draft.originalTravelId(),
                    draft.travelRecordId(),
                    new TravelRecordUpdateReqDto("  ", null, null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel record title cannot be blank.");
    assertThatThrownBy(
            () ->
                travelRecordService.updateDraft(
                    authenticatedUser,
                    draft.originalTravelId(),
                    draft.travelRecordId(),
                    new TravelRecordUpdateReqDto("title", null, null, 0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel record overall rating must be between 1 and 5.");

    assertThatThrownBy(
            () ->
                travelRecordService.updateDraft(
                    authenticatedUser,
                    travel.id(),
                    draft.travelRecordId(),
                    new TravelRecordUpdateReqDto("title", null, null, 5)))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Travel record not found.");
  }

  @Test
  @DisplayName("복제 요청이 없거나 시작일·제목이 규칙에 맞지 않으면 거부한다")
  void rejectsInvalidCloneToTravelRequests() {
    User author = userRepository.save(createUser("clone-invalid@example.com", "clone-invalid"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(author);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Clone Source");
    TravelRecordResDto published =
        travelRecordService.publish(
            authenticatedUser, draft.originalTravelId(), draft.travelRecordId());
    java.util.UUID recordId = published.travelRecordId();

    assertThatThrownBy(() -> travelRecordService.cloneToTravel(authenticatedUser, recordId, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel clone request is required.");
    assertThatThrownBy(
            () ->
                travelRecordService.cloneToTravel(
                    authenticatedUser,
                    recordId,
                    new TravelRecordCloneToTravelReqDto(
                        "title", null, null, null, null, null, null, null, null, null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel start date is required.");
    assertThatThrownBy(
            () ->
                travelRecordService.cloneToTravel(
                    authenticatedUser,
                    recordId,
                    new TravelRecordCloneToTravelReqDto(
                        "   ",
                        LocalDate.of(2026, 10, 1),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel title cannot be blank.");
    assertThatThrownBy(
            () ->
                travelRecordService.cloneToTravel(
                    authenticatedUser,
                    recordId,
                    new TravelRecordCloneToTravelReqDto(
                        "0123456789012345",
                        LocalDate.of(2026, 10, 1),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel title must be 15 characters or less.");
  }

  @Test
  @DisplayName("댓글 내용이 비었거나 1000자를 넘으면 거부한다")
  void rejectsInvalidCommentRequests() {
    User author = userRepository.save(createUser("comment-invalid@example.com", "comment-invalid"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(author);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Comment Source");
    TravelRecordResDto published =
        travelRecordService.publish(
            authenticatedUser, draft.originalTravelId(), draft.travelRecordId());
    java.util.UUID recordId = published.travelRecordId();
    String tooLong = "a".repeat(1001);

    assertThatThrownBy(
            () ->
                travelRecordService.createComment(
                    authenticatedUser, recordId, new TravelRecordCommentCreateReqDto("  ")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel record comment content is required.");
    assertThatThrownBy(
            () ->
                travelRecordService.createComment(
                    authenticatedUser, recordId, new TravelRecordCommentCreateReqDto(tooLong)))
        .isInstanceOf(IllegalArgumentException.class);

    TravelRecordCommentResDto comment =
        travelRecordService.createComment(
            authenticatedUser, recordId, new TravelRecordCommentCreateReqDto("ok"));

    assertThatThrownBy(
            () ->
                travelRecordService.updateComment(
                    authenticatedUser,
                    recordId,
                    comment.commentId(),
                    new TravelRecordCommentUpdateReqDto("  ")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel record comment content is required.");
    assertThatThrownBy(
            () ->
                travelRecordService.updateComment(
                    authenticatedUser,
                    recordId,
                    comment.commentId(),
                    new TravelRecordCommentUpdateReqDto(tooLong)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("장소 리뷰의 평점과 체류 시간이 범위를 벗어나면 거부한다")
  void rejectsInvalidPlaceReviewRequests() {
    User author = userRepository.save(createUser("review-invalid@example.com", "review-invalid"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(author);
    TravelResDto travel = createCompletedTravel(authenticatedUser);
    PlanResDto firstDay =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    PlanPlaceResDto place =
        createPlace(authenticatedUser, firstDay.planId(), 1, "Busan Station", "Busan");

    assertThatThrownBy(
            () ->
                travelRecordService.createPlaceReview(
                    authenticatedUser,
                    travel.id(),
                    place.planPlaceId(),
                    new PlaceReviewCreateReqDto(null, "no rating")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Place review rating is required.");
    assertThatThrownBy(
            () ->
                travelRecordService.createPlaceReview(
                    authenticatedUser,
                    travel.id(),
                    place.planPlaceId(),
                    new PlaceReviewCreateReqDto(6, "too high")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Place review rating must be between 1 and 5.");
    assertThatThrownBy(
            () ->
                travelRecordService.createPlaceReview(
                    authenticatedUser,
                    travel.id(),
                    place.planPlaceId(),
                    new PlaceReviewCreateReqDto(5, "negative stay", null, -1, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Stay minutes must be 0 or greater.");

    travelRecordService.createPlaceReview(
        authenticatedUser, travel.id(), place.planPlaceId(), new PlaceReviewCreateReqDto(5, "ok"));

    assertThatThrownBy(
            () ->
                travelRecordService.updatePlaceReview(
                    authenticatedUser,
                    travel.id(),
                    place.planPlaceId(),
                    new PlaceReviewUpdateReqDto(0, "too low")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Place review rating must be between 1 and 5.");
  }

  @Test
  @DisplayName("장소 리뷰 태그와 미디어 키의 개수·길이 제한을 검증한다")
  void rejectsOversizedPlaceReviewTagsAndMediaKeys() {
    User author = userRepository.save(createUser("review-limits@example.com", "review-limits"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(author);
    TravelResDto travel = createCompletedTravel(authenticatedUser);
    PlanResDto firstDay =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    PlanPlaceResDto place =
        createPlace(authenticatedUser, firstDay.planId(), 1, "Busan Station", "Busan");

    assertThatThrownBy(
            () ->
                travelRecordService.createPlaceReview(
                    authenticatedUser,
                    travel.id(),
                    place.planPlaceId(),
                    new PlaceReviewCreateReqDto(5, "tag too long", List.of("a".repeat(31)))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Place review tag must be");

    List<String> tooManyMediaKeys =
        java.util.stream.IntStream.rangeClosed(1, 21).mapToObj(i -> "uploads/" + i).toList();
    assertThatThrownBy(
            () ->
                travelRecordService.createPlaceReview(
                    authenticatedUser,
                    travel.id(),
                    place.planPlaceId(),
                    new PlaceReviewCreateReqDto(5, "too many media", null, 30, tooManyMediaKeys)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Place review media file keys must be");

    assertThatThrownBy(
            () ->
                travelRecordService.createPlaceReview(
                    authenticatedUser,
                    travel.id(),
                    place.planPlaceId(),
                    new PlaceReviewCreateReqDto(
                        5, "media key too long", null, 30, List.of("a".repeat(501)))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Place review media file key must be");
  }

  @Test
  @DisplayName("완료되지 않은 여행으로는 기록 초안을 만들 수 없다")
  void rejectsDraftForIncompleteTravel() {
    User author = userRepository.save(createUser("incomplete@example.com", "incomplete"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(author);
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

    assertThatThrownBy(
            () ->
                travelRecordService.createDraft(
                    authenticatedUser, travel.id(), new TravelRecordCreateReqDto("t", null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Only completed travels can be recorded.");
  }

  @Test
  @DisplayName("피드 size가 1~50 범위를 벗어나면 거부한다")
  void rejectsFeedSizeOutOfRange() {
    assertThatThrownBy(() -> travelRecordService.getLatestFeed(null, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Feed size must be between 1 and 50.");
    assertThatThrownBy(() -> travelRecordService.getLatestFeed(null, 51))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Feed size must be between 1 and 50.");
  }

  @Test
  @DisplayName("발행 상태가 아닌 기록은 다시 공개할 수 없다")
  void rejectsRepublishOfNonHiddenRecord() {
    User author = userRepository.save(createUser("republish-bad@example.com", "republish-bad"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(author);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Republish");

    assertThatThrownBy(
            () -> travelRecordService.republishMyRecord(authenticatedUser, draft.travelRecordId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Only hidden travel records can be republished.");
  }

  @Test
  @DisplayName("평점 없는 초안은 발행할 수 없다")
  void rejectsPublishWithoutOverallRating() {
    User author = userRepository.save(createUser("no-title@example.com", "no-title"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(author);
    TravelResDto travel = createCompletedTravel(authenticatedUser);
    PlanResDto firstDay =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    createPlace(authenticatedUser, firstDay.planId(), 1, "Busan Station", "Busan");
    TravelRecordResDto draft =
        travelRecordService.createDraft(authenticatedUser, travel.id(), null);

    assertThatThrownBy(
            () ->
                travelRecordService.publish(authenticatedUser, travel.id(), draft.travelRecordId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Travel record overall rating is required.");
  }

  @Test
  @DisplayName("여행 멤버가 아니면 기록 초안을 만들 수 없다")
  void rejectsDraftFromNonMember() {
    User owner = userRepository.save(createUser("record-owner@example.com", "record-owner"));
    User outsider = userRepository.save(createUser("record-out@example.com", "record-out"));
    TravelResDto travel = createCompletedTravel(AuthenticatedUser.from(owner));

    assertThatThrownBy(
            () ->
                travelRecordService.createDraft(
                    AuthenticatedUser.from(outsider),
                    travel.id(),
                    new TravelRecordCreateReqDto("t", null, null)))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("User is not a travel member.");
  }

  @Test
  @DisplayName("본문 없는 수정 요청은 기존 초안을 그대로 반환한다")
  void updateDraftWithNullRequestReturnsUnchangedRecord() {
    User author = userRepository.save(createUser("null-update@example.com", "null-update"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(author);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Unchanged");

    TravelRecordResDto result =
        travelRecordService.updateDraft(
            authenticatedUser, draft.originalTravelId(), draft.travelRecordId(), null);

    assertThat(result.travelRecordId()).isEqualTo(draft.travelRecordId());
    assertThat(result.title()).isEqualTo(draft.title());
  }

  @Test
  @DisplayName("본문 없는 리뷰 수정 요청은 기존 리뷰를 그대로 반환한다")
  void updatePlaceReviewWithNullRequestReturnsUnchangedReview() {
    User author = userRepository.save(createUser("null-review@example.com", "null-review"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(author);
    DraftWithPlanPlace reviewed =
        createDraftWithOneReviewedPlace(authenticatedUser, "Null Review", 4, "원본", null);

    PlaceReviewResDto result =
        travelRecordService.updatePlaceReview(
            authenticatedUser,
            reviewed.draft().originalTravelId(),
            reviewed.planPlace().planPlaceId(),
            null);

    assertThat(result.rating()).isEqualTo(4);
    assertThat(result.content()).isEqualTo("원본");
  }

  @Test
  @DisplayName("미디어 키를 넘긴 리뷰 수정은 첨부를 교체한다")
  void updatePlaceReviewReplacesMedia() {
    User author = userRepository.save(createUser("media-review@example.com", "media-review"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(author);
    DraftWithPlanPlace reviewed =
        createDraftWithOneReviewedPlace(authenticatedUser, "Media Review", 4, "원본", null);

    PlaceReviewResDto result =
        travelRecordService.updatePlaceReview(
            authenticatedUser,
            reviewed.draft().originalTravelId(),
            reviewed.planPlace().planPlaceId(),
            new PlaceReviewUpdateReqDto(5, "수정", null, 60, List.of("uploads/a.jpg")));

    assertThat(result.rating()).isEqualTo(5);
    assertThat(result.mediaUrls()).hasSize(1);
  }

  @Test
  @DisplayName("사용자 없이도 정렬 옵션까지 포함해 피드를 조회할 수 있다")
  void getLatestFeedWithoutUserSupportsAllFilters() {
    User author = userRepository.save(createUser("anon-feed@example.com", "anon-feed"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(author);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Anon Feed");
    travelRecordService.publish(
        authenticatedUser, draft.originalTravelId(), draft.travelRecordId());

    TravelRecordFeedPageResDto result =
        travelRecordService.getLatestFeed(
            null, null, null, null, null, null, null, TravelRecordFeedSort.LATEST);

    assertThat(result.items()).isNotEmpty();
  }

  @Test
  @DisplayName("provider 기반 장소별 기록 조회 오버로드도 같은 결과를 반환한다")
  void getTravelRecordsByPlaceProviderOverloads() {
    User author = userRepository.save(createUser("place-feed@example.com", "place-feed"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(author);
    DraftWithPlanPlace reviewed =
        createDraftWithOneReviewedPlace(authenticatedUser, "Place Feed", 5, "좋아요", null);
    travelRecordService.publish(
        authenticatedUser, reviewed.draft().originalTravelId(), reviewed.draft().travelRecordId());

    assertThat(travelRecordService.getTravelRecordsByPlace(PlaceProvider.GOOGLE, "Busan Station"))
        .isNotEmpty();
    assertThat(
            travelRecordService
                .getTravelRecordsByPlace(PlaceProvider.GOOGLE, "Busan Station", null, 10)
                .items())
        .isNotEmpty();
  }

  @Test
  @DisplayName("형식이 잘못된 피드 커서는 거부한다")
  void rejectsMalformedFeedCursor() {
    String malformed =
        java.util.Base64.getUrlEncoder()
            .encodeToString("a|b|c".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    assertThatThrownBy(() -> travelRecordService.getLatestFeed(malformed, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid feed cursor.");
    assertThatThrownBy(() -> travelRecordService.getLatestFeed("not-base64!!", 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid feed cursor.");
  }

  @Test
  @DisplayName("정렬 정보 없는 예전 형식의 커서도 최신순으로 해석한다")
  void acceptsLegacyTwoValueFeedCursor() {
    User author = userRepository.save(createUser("legacy-cursor@example.com", "legacy-cursor"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(author);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Legacy Cursor");
    travelRecordService.publish(
        authenticatedUser, draft.originalTravelId(), draft.travelRecordId());

    String legacyCursor =
        java.util.Base64.getUrlEncoder()
            .encodeToString(
                (java.time.LocalDateTime.now().plusDays(1)
                        + "|"
                        + java.time.LocalDateTime.now().plusDays(1))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));

    assertThat(travelRecordService.getLatestFeed(legacyCursor, 10).items()).isNotEmpty();
  }

  @Test
  @DisplayName("provider를 포함한 피드 오버로드도 같은 결과를 반환한다")
  void getLatestFeedWithProviderOverload() {
    User author = userRepository.save(createUser("provider-feed@example.com", "provider-feed"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(author);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Provider Feed");
    travelRecordService.publish(
        authenticatedUser, draft.originalTravelId(), draft.travelRecordId());

    TravelRecordFeedPageResDto result =
        travelRecordService.getLatestFeed(
            null,
            10,
            null,
            PlaceProvider.GOOGLE,
            null,
            null,
            null,
            null,
            null,
            TravelRecordFeedSort.LATEST);

    assertThat(result.items()).isNotEmpty();
  }

  @Test
  @DisplayName("본문 없는 내 기록 수정 요청은 기존 기록을 그대로 반환한다")
  void updateMyRecordWithNullRequestReturnsUnchangedRecord() {
    User author = userRepository.save(createUser("my-null@example.com", "my-null"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(author);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "My Unchanged");

    TravelRecordResDto result =
        travelRecordService.updateMyRecord(authenticatedUser, draft.travelRecordId(), null);

    assertThat(result.travelRecordId()).isEqualTo(draft.travelRecordId());
    assertThat(result.title()).isEqualTo(draft.title());
  }

  @Test
  @DisplayName("기록과 요청 모두 제목이 없으면 기본 제목으로 복제한다")
  void cloneToTravelFallsBackToDefaultTitle() {
    User author = userRepository.save(createUser("clone-title@example.com", "clone-title"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(author);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Clone Title");
    TravelRecordResDto published =
        travelRecordService.publish(
            authenticatedUser, draft.originalTravelId(), draft.travelRecordId());

    TravelPlansResDto cloned =
        travelRecordService.cloneToTravel(
            authenticatedUser,
            published.travelRecordId(),
            new TravelRecordCloneToTravelReqDto(
                null,
                LocalDate.of(2026, 10, 1),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

    assertThat(cloned.title()).isEqualTo(published.title());
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
    return createDraftWithOnePlace(authenticatedUser, null);
  }

  private TravelRecordResDto createDraftWithOnePlace(
      AuthenticatedUser authenticatedUser, String title) {
    return createDraftWithOnePlace(authenticatedUser, title, "Busan Station");
  }

  private TravelRecordResDto createDraftWithOnePlace(
      AuthenticatedUser authenticatedUser, String title, String placeName) {
    return createDraftWithOnePlace(authenticatedUser, title, placeName, "Busan");
  }

  private TravelRecordResDto createDraftWithOnePlace(
      AuthenticatedUser authenticatedUser, String title, String placeName, String address) {
    TravelResDto travel = createCompletedTravel(authenticatedUser);
    PlanResDto firstDay =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    createPlace(authenticatedUser, firstDay.planId(), 1, placeName, address);

    return travelRecordService.createDraft(
        authenticatedUser, travel.id(), new TravelRecordCreateReqDto(title, null, null, 5));
  }

  private DraftWithPlanPlace createDraftWithOneReviewedPlace(
      AuthenticatedUser authenticatedUser,
      String title,
      Integer rating,
      String content,
      List<String> tags) {
    TravelResDto travel = createCompletedTravel(authenticatedUser);
    PlanResDto firstDay =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    PlanPlaceResDto place =
        createPlace(authenticatedUser, firstDay.planId(), 1, "Busan Station", "Busan");

    travelRecordService.createPlaceReview(
        authenticatedUser,
        travel.id(),
        place.planPlaceId(),
        new PlaceReviewCreateReqDto(rating, content, tags));

    TravelRecordResDto draft =
        travelRecordService.createDraft(
            authenticatedUser, travel.id(), new TravelRecordCreateReqDto(title, null, null, 5));

    return new DraftWithPlanPlace(draft, place);
  }

  private record DraftWithPlanPlace(TravelRecordResDto draft, PlanPlaceResDto planPlace) {}

  private PlanPlaceResDto createPlace(
      AuthenticatedUser authenticatedUser, java.util.UUID planId, Integer sequence, String name) {
    return createPlace(authenticatedUser, planId, sequence, name, "Busan");
  }

  private PlanPlaceResDto createPlace(
      AuthenticatedUser authenticatedUser,
      java.util.UUID planId,
      Integer sequence,
      String name,
      String address) {
    return travelService.createPlanPlace(
        authenticatedUser,
        planId,
        new PlanPlaceCreateReqDto(
            sequence,
            name,
            address,
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
