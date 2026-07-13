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
import com.butingbe.domain.travelrecord.dto.response.PlaceReviewSummaryResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordBookmarkResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordFeedPageResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordFeedResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordLikeResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordManageResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordResDto;
import com.butingbe.domain.travelrecord.entity.TravelRecordStatus;
import com.butingbe.domain.travelrecord.repository.PlaceReviewRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordRepository;
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
import java.util.List;
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
  @Autowired private TravelRecordRepository travelRecordRepository;
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
    assertThat(result.days().getFirst().places().getFirst().placeName())
        .isEqualTo("Busan Station");
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
        userRepository.save(createUser("record-feed-page-first@example.com", "record-feed-page-first"));
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
    TravelRecordResDto publishedDraft = createDraftWithOnePlace(authenticatedUser, "Published Detail");
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
        userRepository.save(createUser("record-my-detail-owner@example.com", "record-my-detail-owner"));
    User outsider =
        userRepository.save(
            createUser("record-my-detail-outsider@example.com", "record-my-detail-outsider"));
    AuthenticatedUser ownerUser = AuthenticatedUser.from(owner);
    TravelRecordResDto draft = createDraftWithOnePlace(ownerUser, "Owner Detail");

    assertThatThrownBy(
            () -> travelRecordService.getMyRecord(AuthenticatedUser.from(outsider), draft.travelRecordId()))
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
            new TravelRecordUpdateReqDto("Draft After", "Draft content", "https://image.test/draft"));
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
            new TravelRecordUpdateReqDto("Hidden After", "Hidden content", "https://image.test/hidden"));

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
        userRepository.save(createUser("record-my-update-null@example.com", "record-my-update-null"));
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
        draft.travelRecordId(),
        place.travelRecordPlaceId(),
        new PlaceReviewCreateReqDto(5, "Before hidden"));
    TravelRecordResDto published =
        travelRecordService.publish(
            authenticatedUser, draft.originalTravelId(), draft.travelRecordId());

    TravelRecordResDto result =
        travelRecordService.hideMyRecord(authenticatedUser, published.travelRecordId());

    assertThat(result.status()).isEqualTo(TravelRecordStatus.HIDDEN);
    assertThat(travelRecordService.getMyRecord(authenticatedUser, published.travelRecordId()).status())
        .isEqualTo(TravelRecordStatus.HIDDEN);
    assertThatThrownBy(() -> travelRecordService.getPublished(published.travelRecordId()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Travel record not found.");
    assertThat(travelRecordService.getLatestFeed(null, null).items())
        .extracting(TravelRecordFeedResDto::travelRecordId)
        .doesNotContain(published.travelRecordId());
    assertThat(travelRecordService.getPlaceReviewSummary(PlaceProvider.GOOGLE, "Busan Station")
            .reviews())
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
            () -> travelRecordService.hideMyRecord(AuthenticatedUser.from(outsider), draft.travelRecordId()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("User is not the travel record author.");
  }

  @Test
  @DisplayName("author can republish own hidden travel record")
  void republishMyRecordRepublishesHiddenPublishedRecord() {
    User user =
        userRepository.save(createUser("record-republish@example.com", "record-republish"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Republish Me");
    var place = draft.days().getFirst().places().getFirst();
    travelRecordService.createPlaceReview(
        authenticatedUser,
        draft.originalTravelId(),
        draft.travelRecordId(),
        place.travelRecordPlaceId(),
        new PlaceReviewCreateReqDto(5, "Back again"));
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
    assertThat(travelRecordService.getPlaceReviewSummary(PlaceProvider.GOOGLE, "Busan Station")
            .reviews())
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
        userRepository.save(createUser("record-bookmark-author@example.com", "record-bookmark-author"));
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
            createUser("record-bookmark-duplicate-author@example.com", "record-bookmark-duplicate-author"));
    User user =
        userRepository.save(
            createUser("record-bookmark-duplicate-user@example.com", "record-bookmark-duplicate-user"));
    AuthenticatedUser authorUser = AuthenticatedUser.from(author);
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authorUser, "Duplicate Bookmark");
    TravelRecordResDto published =
        travelRecordService.publish(authorUser, draft.originalTravelId(), draft.travelRecordId());
    travelRecordService.bookmarkTravelRecord(authenticatedUser, published.travelRecordId());

    assertThatThrownBy(
            () -> travelRecordService.bookmarkTravelRecord(authenticatedUser, published.travelRecordId()))
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
            () -> travelRecordService.bookmarkTravelRecord(authenticatedUser, draft.travelRecordId()))
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
        userRepository.save(createUser("record-unbookmark-user@example.com", "record-unbookmark-user"));
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
        userRepository.save(createUser("record-bookmark-list-user@example.com", "record-bookmark-list-user"));
    AuthenticatedUser authorUser = AuthenticatedUser.from(author);
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto firstDraft = createDraftWithOnePlace(authorUser, "First Bookmark");
    TravelRecordResDto secondDraft = createDraftWithOnePlace(authorUser, "Second Bookmark");
    TravelRecordResDto firstPublished =
        travelRecordService.publish(authorUser, firstDraft.originalTravelId(), firstDraft.travelRecordId());
    TravelRecordResDto secondPublished =
        travelRecordService.publish(authorUser, secondDraft.originalTravelId(), secondDraft.travelRecordId());
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
            () -> travelRecordService.likeTravelRecord(authenticatedUser, published.travelRecordId()))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("Travel record like already exists.");
  }

  @Test
  @DisplayName("like rejects non-published travel record")
  void likeTravelRecordRejectsNonPublishedRecord() {
    User user =
        userRepository.save(createUser("record-like-draft-user@example.com", "record-like-draft-user"));
    AuthenticatedUser authenticatedUser = AuthenticatedUser.from(user);
    TravelRecordResDto draft = createDraftWithOnePlace(authenticatedUser, "Draft Like");

    assertThatThrownBy(
            () -> travelRecordService.likeTravelRecord(authenticatedUser, draft.travelRecordId()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Travel record not found.");
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
    TravelRecordResDto firstDraft = createDraftWithOnePlace(firstAuthenticatedUser, "First Record");
    TravelRecordResDto secondDraft =
        createDraftWithOnePlace(secondAuthenticatedUser, "Second Record");
    TravelRecordResDto hiddenDraft = createDraftWithOnePlace(draftAuthenticatedUser, "Draft Record");
    var firstPlace = firstDraft.days().getFirst().places().getFirst();
    var secondPlace = secondDraft.days().getFirst().places().getFirst();
    var hiddenPlace = hiddenDraft.days().getFirst().places().getFirst();
    travelRecordService.createPlaceReview(
        firstAuthenticatedUser,
        firstDraft.originalTravelId(),
        firstDraft.travelRecordId(),
        firstPlace.travelRecordPlaceId(),
        new PlaceReviewCreateReqDto(4, "Good route"));
    travelRecordService.createPlaceReview(
        secondAuthenticatedUser,
        secondDraft.originalTravelId(),
        secondDraft.travelRecordId(),
        secondPlace.travelRecordPlaceId(),
        new PlaceReviewCreateReqDto(5, "Perfect stop"));
    travelRecordService.createPlaceReview(
        draftAuthenticatedUser,
        hiddenDraft.originalTravelId(),
        hiddenDraft.travelRecordId(),
        hiddenPlace.travelRecordPlaceId(),
        new PlaceReviewCreateReqDto(1, "Still draft"));
    travelRecordService.publish(
        firstAuthenticatedUser, firstDraft.originalTravelId(), firstDraft.travelRecordId());
    travelRecordService.publish(
        secondAuthenticatedUser, secondDraft.originalTravelId(), secondDraft.travelRecordId());

    PlaceReviewSummaryResDto result =
        travelRecordService.getPlaceReviewSummary(PlaceProvider.GOOGLE, "Busan Station");

    assertThat(result.provider()).isEqualTo(PlaceProvider.GOOGLE);
    assertThat(result.providerPlaceId()).isEqualTo("Busan Station");
    assertThat(result.reviewCount()).isEqualTo(2);
    assertThat(result.averageRating()).isEqualTo(4.5);
    assertThat(result.ratingCounts()).containsEntry(1, 0L);
    assertThat(result.ratingCounts()).containsEntry(4, 1L);
    assertThat(result.ratingCounts()).containsEntry(5, 1L);
    assertThat(result.reviews())
        .extracting(PlaceReviewSummaryResDto.PlaceReviewItemResDto::content)
        .containsExactlyInAnyOrder("Perfect stop", "Good route");
    assertThat(result.reviews())
        .extracting(PlaceReviewSummaryResDto.PlaceReviewItemResDto::travelRecordId)
        .doesNotContain(hiddenDraft.travelRecordId());
  }

  @Test
  @DisplayName("place review summary returns empty aggregate when there are no public reviews")
  void getPlaceReviewSummaryReturnsEmptyAggregate() {
    PlaceReviewSummaryResDto result =
        travelRecordService.getPlaceReviewSummary(PlaceProvider.GOOGLE, "missing-place");

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
        userRepository.save(
            createUser("record-place-first@example.com", "record-place-first"));
    User secondUser =
        userRepository.save(
            createUser("record-place-second@example.com", "record-place-second"));
    User hiddenUser =
        userRepository.save(
            createUser("record-place-hidden@example.com", "record-place-hidden"));
    User otherUser =
        userRepository.save(
            createUser("record-place-other@example.com", "record-place-other"));
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
        travelRecordService.getTravelRecordsByPlace(PlaceProvider.GOOGLE, "Busan Station");

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
        travelRecordService.getTravelRecordsByPlace(PlaceProvider.GOOGLE, "missing-place");

    assertThat(result).isEmpty();
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
    return createDraftWithOnePlace(authenticatedUser, null);
  }

  private TravelRecordResDto createDraftWithOnePlace(
      AuthenticatedUser authenticatedUser, String title) {
    return createDraftWithOnePlace(authenticatedUser, title, "Busan Station");
  }

  private TravelRecordResDto createDraftWithOnePlace(
      AuthenticatedUser authenticatedUser, String title, String placeName) {
    TravelResDto travel = createCompletedTravel(authenticatedUser);
    PlanResDto firstDay =
        travelService.createPlan(
            authenticatedUser, travel.id(), new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));
    createPlace(authenticatedUser, firstDay.planId(), 1, placeName);

    return travelRecordService.createDraft(
        authenticatedUser,
        travel.id(),
        title == null ? null : new TravelRecordCreateReqDto(title, null, null));
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
