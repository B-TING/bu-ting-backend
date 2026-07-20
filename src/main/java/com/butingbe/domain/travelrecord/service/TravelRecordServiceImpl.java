package com.butingbe.domain.travelrecord.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travel.entity.Plan;
import com.butingbe.domain.travel.entity.PlanPlace;
import com.butingbe.domain.travel.entity.PlanRoute;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.entity.TravelStatus;
import com.butingbe.domain.travel.repository.PlanPlaceRepository;
import com.butingbe.domain.travel.repository.PlanRepository;
import com.butingbe.domain.travel.repository.PlanRouteRepository;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travelrecord.dto.request.PlaceReviewCreateReqDto;
import com.butingbe.domain.travelrecord.dto.request.PlaceReviewUpdateReqDto;
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
import com.butingbe.domain.travelrecord.dto.response.TravelRecordResDto.TravelRecordDayResDto;
import com.butingbe.domain.travelrecord.entity.PlaceReview;
import com.butingbe.domain.travelrecord.entity.PlaceReviewImage;
import com.butingbe.domain.travelrecord.entity.TravelRecord;
import com.butingbe.domain.travelrecord.entity.TravelRecordBookmark;
import com.butingbe.domain.travelrecord.entity.TravelRecordComment;
import com.butingbe.domain.travelrecord.entity.TravelRecordDay;
import com.butingbe.domain.travelrecord.entity.TravelRecordLike;
import com.butingbe.domain.travelrecord.entity.TravelRecordPlace;
import com.butingbe.domain.travelrecord.entity.TravelRecordRoute;
import com.butingbe.domain.travelrecord.entity.TravelRecordStatus;
import com.butingbe.domain.travelrecord.repository.PlaceReviewRepository;
import com.butingbe.domain.travelrecord.repository.PlaceReviewImageRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordBookmarkRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordCommentRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordDayRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordLikeRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordPlaceRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordRouteRepository;
import com.butingbe.domain.travelteam.repository.TravelMemberRepository;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.DuplicateResourceException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelRecordServiceImpl implements TravelRecordService {

  private static final String DEFAULT_TITLE = "여행 기록";
  private static final int DEFAULT_FEED_SIZE = 20;
  private static final int MAX_FEED_SIZE = 50;
  private static final int MAX_COMMENT_CONTENT_LENGTH = 1000;
  private static final int MAX_PLACE_REVIEW_TAG_COUNT = 10;
  private static final int MAX_PLACE_REVIEW_TAG_LENGTH = 30;
  private static final int MAX_PLACE_REVIEW_MEDIA_COUNT = 20;
  private static final int MAX_PLACE_REVIEW_MEDIA_URL_LENGTH = 1000;

  private final TravelRepository travelRepository;
  private final PlanRepository planRepository;
  private final PlanPlaceRepository planPlaceRepository;
  private final PlanRouteRepository planRouteRepository;
  private final TravelMemberRepository travelMemberRepository;
  private final UserRepository userRepository;
  private final TravelRecordRepository travelRecordRepository;
  private final TravelRecordDayRepository travelRecordDayRepository;
  private final TravelRecordPlaceRepository travelRecordPlaceRepository;
  private final TravelRecordRouteRepository travelRecordRouteRepository;
  private final PlaceReviewRepository placeReviewRepository;
  private final PlaceReviewImageRepository placeReviewImageRepository;
  private final TravelRecordBookmarkRepository travelRecordBookmarkRepository;
  private final TravelRecordLikeRepository travelRecordLikeRepository;
  private final TravelRecordCommentRepository travelRecordCommentRepository;

  @Override
  @Transactional
  public TravelRecordResDto createDraft(
      AuthenticatedUser authenticatedUser, UUID travelId, TravelRecordCreateReqDto request) {
    User author = findAuthenticatedUser(authenticatedUser);
    Travel travel = findTravel(travelId);
    validateTravelMember(travelId, author.getId());
    validateCompletedTravel(travel);
    validateCreateRequest(request);
    validateNotDuplicated(travelId, author.getId());

    TravelRecord travelRecord =
        travelRecordRepository.save(
            TravelRecord.builder()
                .originalTravel(travel)
                .author(author)
                .title(resolveTitle(travel, request))
                .content(request == null ? null : request.content())
                .coverImageUrl(request == null ? null : request.coverImageUrl())
                .overallRating(request == null ? null : request.overallRating())
                .travelStartDate(travel.getStartDate())
                .travelEndDate(travel.getEndDate())
                .status(TravelRecordStatus.DRAFT)
                .build());

    copyItinerarySnapshot(travelId, travelRecord);

    return toResponse(travelRecord);
  }

  @Override
  public TravelRecordResDto getDraft(
      AuthenticatedUser authenticatedUser, UUID travelId, UUID travelRecordId) {
    User author = findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validateDraftBelongsToTravel(travelRecord, travelId);
    validateAuthor(travelRecord, author.getId());
    validateDraft(travelRecord);

    return toResponse(travelRecord);
  }

  @Override
  @Transactional
  public TravelRecordResDto updateDraft(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      UUID travelRecordId,
      TravelRecordUpdateReqDto request) {
    User author = findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validateDraftBelongsToTravel(travelRecord, travelId);
    validateAuthor(travelRecord, author.getId());
    validateDraft(travelRecord);
    validateUpdateRequest(request);

    if (request == null) {
      return toResponse(travelRecord);
    }

    travelRecord.updateContent(
        request.title(), request.content(), request.coverImageUrl(), request.overallRating());

    return toResponse(travelRecord);
  }

  @Override
  @Transactional
  public TravelRecordResDto publish(
      AuthenticatedUser authenticatedUser, UUID travelId, UUID travelRecordId) {
    User author = findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validateDraftBelongsToTravel(travelRecord, travelId);
    validateAuthor(travelRecord, author.getId());
    validateDraft(travelRecord);
    validatePublishable(travelRecord);

    travelRecord.publish(LocalDateTime.now());

    return toResponse(travelRecord);
  }

  @Override
  @Transactional
  public TravelRecordResDto getPublished(UUID travelRecordId) {
    return getPublished(null, travelRecordId);
  }

  @Override
  @Transactional
  public TravelRecordResDto getPublished(AuthenticatedUser authenticatedUser, UUID travelRecordId) {
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validatePublished(travelRecord);
    travelRecord.increaseViewCount();

    return toResponse(travelRecord, isLikedBy(authenticatedUser, travelRecord.getId()));
  }

  @Override
  public TravelRecordFeedPageResDto getLatestFeed(String cursor, Integer size) {
    return getLatestFeed(null, cursor, size, null, null, null, null, null, null, null);
  }

  @Override
  public TravelRecordFeedPageResDto getLatestFeed(
      String cursor,
      Integer size,
      String keyword,
      PlaceProvider provider,
      String providerPlaceId,
      LocalDate travelStartDate,
      LocalDate travelEndDate) {
    return getLatestFeed(
        null,
        cursor,
        size,
        keyword,
        providerPlaceId,
        travelStartDate,
        travelEndDate,
        null,
        null,
        null);
  }

  @Override
  public TravelRecordFeedPageResDto getLatestFeed(
      String cursor,
      Integer size,
      String keyword,
      PlaceProvider provider,
      String providerPlaceId,
      LocalDate travelStartDate,
      LocalDate travelEndDate,
      String region,
      String city) {
    return getLatestFeed(
        null,
        cursor,
        size,
        keyword,
        providerPlaceId,
        travelStartDate,
        travelEndDate,
        region,
        city,
        null);
  }

  @Override
  public TravelRecordFeedPageResDto getLatestFeed(
      String cursor,
      Integer size,
      String keyword,
      PlaceProvider provider,
      String providerPlaceId,
      LocalDate travelStartDate,
      LocalDate travelEndDate,
      TravelRecordFeedSort sort) {
    return getLatestFeed(
        null,
        cursor,
        size,
        keyword,
        providerPlaceId,
        travelStartDate,
        travelEndDate,
        null,
        null,
        sort);
  }

  @Override
  public TravelRecordFeedPageResDto getLatestFeed(
      String cursor,
      Integer size,
      String keyword,
      PlaceProvider provider,
      String providerPlaceId,
      LocalDate travelStartDate,
      LocalDate travelEndDate,
      String region,
      String city,
      TravelRecordFeedSort sort) {
    return getLatestFeed(
        null,
        cursor,
        size,
        keyword,
        providerPlaceId,
        travelStartDate,
        travelEndDate,
        region,
        city,
        sort);
  }

  @Override
  public TravelRecordFeedPageResDto getLatestFeed(
      AuthenticatedUser authenticatedUser,
      String cursor,
      Integer size,
      String keyword,
      String placeId,
      LocalDate travelStartDate,
      LocalDate travelEndDate,
      String region,
      String city,
      TravelRecordFeedSort sort) {
    TravelRecordFeedSort feedSort = sort == null ? TravelRecordFeedSort.LATEST : sort;
    int pageSize = resolveFeedSize(size);
    FeedCursor feedCursor = decodeFeedCursor(cursor);
    validateFeedCursorSort(feedCursor, feedSort);
    FeedSearchCondition searchCondition =
        resolveFeedSearchCondition(
            keyword, placeId, travelStartDate, travelEndDate, region, city);
    PageRequest pageRequest = PageRequest.of(0, pageSize + 1);
    List<TravelRecord> fetchedRecords = findFeedRecords(feedCursor, searchCondition, feedSort, pageRequest);
    boolean hasNext = fetchedRecords.size() > pageSize;
    List<TravelRecord> pageRecords =
        hasNext ? fetchedRecords.subList(0, pageSize) : fetchedRecords;
    List<TravelRecordFeedResDto> items = toFeedResponses(pageRecords, authenticatedUser);

    return new TravelRecordFeedPageResDto(
        items,
        hasNext ? encodeFeedCursor(pageRecords.getLast(), feedSort) : null,
        hasNext);
  }

  @Override
  public List<TravelRecordManageResDto> getMyRecords(AuthenticatedUser authenticatedUser) {
    User author = findAuthenticatedUser(authenticatedUser);

    return travelRecordRepository.findByAuthor_IdOrderByCreatedAtDesc(author.getId()).stream()
        .map(TravelRecordManageResDto::from)
        .toList();
  }

  @Override
  public TravelRecordResDto getMyRecord(
      AuthenticatedUser authenticatedUser, UUID travelRecordId) {
    User author = findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validateAuthor(travelRecord, author.getId());

    return toResponse(travelRecord, isLikedBy(authenticatedUser, travelRecord.getId()));
  }

  @Override
  @Transactional
  public TravelRecordResDto updateMyRecord(
      AuthenticatedUser authenticatedUser, UUID travelRecordId, TravelRecordUpdateReqDto request) {
    User author = findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validateAuthor(travelRecord, author.getId());
    validateUpdateRequest(request);

    if (request == null) {
      return toResponse(travelRecord);
    }

    travelRecord.updateContent(
        request.title(), request.content(), request.coverImageUrl(), request.overallRating());

    return toResponse(travelRecord);
  }

  @Override
  @Transactional
  public TravelRecordResDto hideMyRecord(
      AuthenticatedUser authenticatedUser, UUID travelRecordId) {
    User author = findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validateAuthor(travelRecord, author.getId());

    travelRecord.hide();

    return toResponse(travelRecord);
  }

  @Override
  @Transactional
  public TravelRecordResDto republishMyRecord(
      AuthenticatedUser authenticatedUser, UUID travelRecordId) {
    User author = findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validateAuthor(travelRecord, author.getId());
    validateRepublishable(travelRecord);

    travelRecord.republish();

    return toResponse(travelRecord);
  }

  @Override
  @Transactional
  public TravelRecordBookmarkResDto bookmarkTravelRecord(
      AuthenticatedUser authenticatedUser, UUID travelRecordId) {
    User user = findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validatePublished(travelRecord);
    validateBookmarkNotDuplicated(user.getId(), travelRecordId);

    TravelRecordBookmark bookmark =
        travelRecordBookmarkRepository.saveAndFlush(
            TravelRecordBookmark.builder().user(user).travelRecord(travelRecord).build());

    return TravelRecordBookmarkResDto.from(bookmark);
  }

  @Override
  @Transactional
  public void unbookmarkTravelRecord(
      AuthenticatedUser authenticatedUser, UUID travelRecordId) {
    User user = findAuthenticatedUser(authenticatedUser);

    travelRecordBookmarkRepository
        .findByUser_IdAndTravelRecord_Id(user.getId(), travelRecordId)
        .ifPresent(travelRecordBookmarkRepository::delete);
  }

  @Override
  public List<TravelRecordBookmarkResDto> getMyBookmarkedRecords(
      AuthenticatedUser authenticatedUser) {
    User user = findAuthenticatedUser(authenticatedUser);

    return travelRecordBookmarkRepository
        .findByUser_IdAndTravelRecord_StatusOrderByCreatedAtDesc(
            user.getId(), TravelRecordStatus.PUBLISHED)
        .stream()
        .map(TravelRecordBookmarkResDto::from)
        .toList();
  }

  @Override
  @Transactional
  public TravelRecordLikeResDto likeTravelRecord(
      AuthenticatedUser authenticatedUser, UUID travelRecordId) {
    User user = findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validatePublished(travelRecord);
    validateLikeNotDuplicated(user.getId(), travelRecordId);

    travelRecord.increaseLikeCount();
    TravelRecordLike like =
        travelRecordLikeRepository.saveAndFlush(
            TravelRecordLike.builder().user(user).travelRecord(travelRecord).build());

    return TravelRecordLikeResDto.from(like);
  }

  @Override
  @Transactional
  public void unlikeTravelRecord(AuthenticatedUser authenticatedUser, UUID travelRecordId) {
    User user = findAuthenticatedUser(authenticatedUser);

    travelRecordLikeRepository
        .findByUser_IdAndTravelRecord_Id(user.getId(), travelRecordId)
        .ifPresent(
            like -> {
              like.getTravelRecord().decreaseLikeCount();
              travelRecordLikeRepository.delete(like);
            });
  }

  @Override
  @Transactional
  public TravelRecordCommentResDto createComment(
      AuthenticatedUser authenticatedUser,
      UUID travelRecordId,
      TravelRecordCommentCreateReqDto request) {
    User author = findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validatePublished(travelRecord);
    validateCommentCreateRequest(request);

    TravelRecordComment comment =
        travelRecordCommentRepository.save(
            TravelRecordComment.builder()
                .travelRecord(travelRecord)
                .author(author)
                .content(request.content().trim())
                .build());

    return TravelRecordCommentResDto.from(comment);
  }

  @Override
  @Transactional(readOnly = true)
  public List<TravelRecordCommentResDto> getComments(UUID travelRecordId) {
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validatePublished(travelRecord);

    return travelRecordCommentRepository
        .findByTravelRecord_IdOrderByCreatedAtAsc(travelRecordId)
        .stream()
        .map(TravelRecordCommentResDto::from)
        .toList();
  }

  @Override
  @Transactional
  public TravelRecordCommentResDto updateComment(
      AuthenticatedUser authenticatedUser,
      UUID travelRecordId,
      UUID commentId,
      TravelRecordCommentUpdateReqDto request) {
    User author = findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validatePublished(travelRecord);
    validateCommentUpdateRequest(request);

    TravelRecordComment comment = findCommentInTravelRecord(commentId, travelRecordId);
    validateCommentAuthor(comment, author.getId());
    comment.update(request.content().trim());

    return TravelRecordCommentResDto.from(comment);
  }

  @Override
  @Transactional
  public void deleteComment(
      AuthenticatedUser authenticatedUser, UUID travelRecordId, UUID commentId) {
    User author = findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validatePublished(travelRecord);

    TravelRecordComment comment = findCommentInTravelRecord(commentId, travelRecordId);
    validateCommentAuthor(comment, author.getId());
    travelRecordCommentRepository.delete(comment);
  }

  @Override
  public List<TravelRecordFeedResDto> getTravelRecordsByPlace(
      PlaceProvider provider, String providerPlaceId) {
    return getTravelRecordsByPlace((AuthenticatedUser) null, providerPlaceId, null, null).items();
  }

  @Override
  public TravelRecordFeedPageResDto getTravelRecordsByPlace(
      PlaceProvider provider, String providerPlaceId, String cursor, Integer size) {
    return getTravelRecordsByPlace((AuthenticatedUser) null, providerPlaceId, cursor, size);
  }

  @Override
  public TravelRecordFeedPageResDto getTravelRecordsByPlace(
      AuthenticatedUser authenticatedUser,
      String placeId,
      String cursor,
      Integer size) {
    validatePlaceId(placeId);
    String normalizedPlaceId = placeId.trim();

    int pageSize = resolveFeedSize(size);
    FeedCursor feedCursor = decodeFeedCursor(cursor);
    validateFeedCursorSort(feedCursor, TravelRecordFeedSort.LATEST);
    PageRequest pageRequest = PageRequest.of(0, pageSize + 1);
    List<TravelRecord> fetchedRecords =
        feedCursor == null
            ? travelRecordRepository.findPublishedRecordsByPlacePage(
                normalizedPlaceId, TravelRecordStatus.PUBLISHED, pageRequest)
            : travelRecordRepository.findPublishedRecordsByPlacePageAfterCursor(
                normalizedPlaceId,
                TravelRecordStatus.PUBLISHED,
                feedCursor.publishedAt(),
                feedCursor.createdAt(),
                pageRequest);
    boolean hasNext = fetchedRecords.size() > pageSize;
    List<TravelRecord> pageRecords =
        hasNext ? fetchedRecords.subList(0, pageSize) : fetchedRecords;
    List<TravelRecordFeedResDto> items = toFeedResponses(pageRecords, authenticatedUser);

    return new TravelRecordFeedPageResDto(
        items,
        hasNext ? encodeFeedCursor(pageRecords.getLast(), TravelRecordFeedSort.LATEST) : null,
        hasNext);
  }

  @Override
  public PlaceReviewSummaryResDto getPlaceReviewSummary(String placeId) {
    validatePlaceId(placeId);

    String normalizedPlaceId = placeId.trim();
    List<PlaceReview> reviews =
        placeReviewRepository.findByPlaceIdAndRecordStatus(
            normalizedPlaceId, TravelRecordStatus.PUBLISHED);

    return PlaceReviewSummaryResDto.of(
        normalizedPlaceId,
        calculateAverageRating(reviews),
        calculateRatingCounts(reviews),
        reviews.stream()
            .map(
                review ->
                    toPlaceReviewSummaryItem(
                        review, findPlaceReviewMediaUrls(review.getId())))
            .toList());
  }

  @Override
  public PlaceReviewSummaryResDto getPlaceReviewSummary(
      PlaceProvider provider, String providerPlaceId) {
    validatePlaceReviewSummaryRequest(provider, providerPlaceId);

    List<PlaceReview> reviews =
        placeReviewRepository.findByPlaceAndRecordStatus(
            provider, providerPlaceId, TravelRecordStatus.PUBLISHED);

    return PlaceReviewSummaryResDto.of(
        providerPlaceId,
        calculateAverageRating(reviews),
        calculateRatingCounts(reviews),
        reviews.stream()
            .map(
                review ->
                    toPlaceReviewSummaryItem(
                        review, findPlaceReviewMediaUrls(review.getId())))
            .toList());
  }

  @Override
  @Transactional
  public PlaceReviewResDto createPlaceReview(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      UUID planPlaceId,
      PlaceReviewCreateReqDto request) {
    User author = findAuthenticatedUser(authenticatedUser);
    validateTravelMember(travelId, author.getId());
    validatePlaceReviewCreateRequest(request);
    List<String> tags = normalizePlaceReviewTags(request.tags());
    List<String> mediaUrls = normalizePlaceReviewMediaUrls(request.mediaUrls());

    PlanPlace planPlace = findPlanPlaceInTravel(planPlaceId, travelId);
    validatePlaceReviewNotDuplicated(planPlaceId, author.getId());

    PlaceReview placeReview =
        placeReviewRepository.save(
            PlaceReview.builder()
                .planPlace(planPlace)
                .author(author)
                .rating(request.rating())
                .stayMinutes(request.stayMinutes())
                .content(request.content())
                .tags(tags)
                .build());

    savePlaceReviewMedia(placeReview, mediaUrls);

    return PlaceReviewResDto.from(placeReview, mediaUrls);
  }

  @Override
  public PlaceReviewResDto getPlaceReview(
      AuthenticatedUser authenticatedUser, UUID travelId, UUID planPlaceId) {
    User author = findAuthenticatedUser(authenticatedUser);
    validateTravelMember(travelId, author.getId());
    findPlanPlaceInTravel(planPlaceId, travelId);

    PlaceReview placeReview = findPlaceReviewByPlanPlaceId(planPlaceId, author.getId());

    return toPlaceReviewResponse(placeReview);
  }

  @Override
  @Transactional
  public PlaceReviewResDto updatePlaceReview(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      UUID planPlaceId,
      PlaceReviewUpdateReqDto request) {
    User author = findAuthenticatedUser(authenticatedUser);
    validateTravelMember(travelId, author.getId());
    findPlanPlaceInTravel(planPlaceId, travelId);
    validatePlaceReviewUpdateRequest(request);

    PlaceReview placeReview = findPlaceReviewByPlanPlaceId(planPlaceId, author.getId());
    if (request == null) {
      return PlaceReviewResDto.from(placeReview);
    }

    List<String> tags = request.tags() == null ? null : normalizePlaceReviewTags(request.tags());
    placeReview.update(request.rating(), request.stayMinutes(), request.content(), tags);
    if (request.mediaUrls() != null) {
      savePlaceReviewMedia(placeReview, normalizePlaceReviewMediaUrls(request.mediaUrls()));
    }
    return toPlaceReviewResponse(placeReview);
  }

  @Override
  @Transactional
  public void deletePlaceReview(
      AuthenticatedUser authenticatedUser, UUID travelId, UUID planPlaceId) {
    User author = findAuthenticatedUser(authenticatedUser);
    validateTravelMember(travelId, author.getId());
    findPlanPlaceInTravel(planPlaceId, travelId);

    PlaceReview placeReview = findPlaceReviewByPlanPlaceId(planPlaceId, author.getId());
    placeReviewImageRepository.deleteByPlaceReview_Id(placeReview.getId());
    placeReviewRepository.delete(placeReview);
  }

  private void copyItinerarySnapshot(UUID travelId, TravelRecord travelRecord) {
    List<Plan> plans = planRepository.findByTravel_IdOrderByDayNumberAsc(travelId);

    for (Plan plan : plans) {
      TravelRecordDay recordDay =
          travelRecordDayRepository.save(
              TravelRecordDay.builder()
                  .travelRecord(travelRecord)
                  .originalPlanId(plan.getId())
                  .dayNumber(plan.getDayNumber())
                  .visitDate(plan.getVisitDate())
                  .build());

      Map<UUID, TravelRecordPlace> copiedPlaceByOriginalId = copyPlaces(plan, recordDay);
      copyRoutes(plan, recordDay, copiedPlaceByOriginalId);
    }
  }

  private Map<UUID, TravelRecordPlace> copyPlaces(Plan plan, TravelRecordDay recordDay) {
    Map<UUID, TravelRecordPlace> copiedPlaceByOriginalId = new HashMap<>();

    for (PlanPlace place : planPlaceRepository.findByPlan_IdOrderBySequenceAsc(plan.getId())) {
      TravelRecordPlace recordPlace =
          travelRecordPlaceRepository.save(
              TravelRecordPlace.builder()
                  .travelRecordDay(recordDay)
                  .originalPlanPlaceId(place.getId())
                  .sequence(place.getSequence())
                  .placeName(place.getPlaceName())
                  .address(place.getAddress())
                  .latitude(place.getLatitude())
                  .longitude(place.getLongitude())
                  .provider(place.getProvider())
                  .providerPlaceId(place.getProviderPlaceId())
                  .durationMinutes(place.getDurationMinutes())
                  .memo(place.getMemo())
                  .scheduledTime(place.getScheduledTime())
                  .visited(place.getVisited())
                  .build());

      copiedPlaceByOriginalId.put(place.getId(), recordPlace);
      copyPlaceReviewSnapshot(place, recordPlace);
    }

    return copiedPlaceByOriginalId;
  }

  private void copyPlaceReviewSnapshot(PlanPlace sourcePlace, TravelRecordPlace recordPlace) {
    placeReviewRepository
        .findByPlanPlace_IdAndAuthor_Id(
            sourcePlace.getId(),
            recordPlace.getTravelRecordDay().getTravelRecord().getAuthor().getId())
        .ifPresent(
            sourceReview -> {
              PlaceReview snapshotReview =
                  PlaceReview.builder()
                      .travelRecordPlace(recordPlace)
                      .author(sourceReview.getAuthor())
                      .rating(sourceReview.getRating())
                      .stayMinutes(sourceReview.getStayMinutes())
                      .content(sourceReview.getContent())
                      .tags(List.copyOf(sourceReview.getTags()))
                      .build();
              List<String> mediaUrls = findPlaceReviewMediaUrls(sourceReview.getId());
              PlaceReview savedReview = placeReviewRepository.save(snapshotReview);
              savePlaceReviewMedia(savedReview, mediaUrls);
            });
  }

  private void copyRoutes(
      Plan plan, TravelRecordDay recordDay, Map<UUID, TravelRecordPlace> copiedPlaceByOriginalId) {
    for (PlanRoute route : planRouteRepository.findByPlan_Id(plan.getId())) {
      TravelRecordPlace fromPlace = copiedPlaceByOriginalId.get(route.getFromPlace().getId());
      TravelRecordPlace toPlace = copiedPlaceByOriginalId.get(route.getToPlace().getId());

      if (fromPlace == null || toPlace == null) {
        continue;
      }

      travelRecordRouteRepository.save(
          TravelRecordRoute.builder()
              .travelRecordDay(recordDay)
              .fromPlace(fromPlace)
              .toPlace(toPlace)
              .transportType(route.getTransportType())
              .durationMinutes(route.getDurationMinutes())
              .distanceMeters(route.getDistanceMeters())
              .provider(route.getProvider())
              .calculatedAt(route.getCalculatedAt())
              .build());
    }
  }

  private TravelRecordResDto toResponse(TravelRecord travelRecord) {
    return toResponse(travelRecord, false);
  }

  private TravelRecordResDto toResponse(TravelRecord travelRecord, boolean likedByMe) {
    List<TravelRecordDayResDto> days =
        travelRecordDayRepository.findByTravelRecord_IdOrderByDayNumberAsc(travelRecord.getId())
            .stream()
            .map(this::toDayResponse)
            .toList();

    return TravelRecordResDto.of(travelRecord, days, likedByMe);
  }

  private boolean isLikedBy(AuthenticatedUser authenticatedUser, UUID travelRecordId) {
    return authenticatedUser != null
        && authenticatedUser.id() != null
        && travelRecordLikeRepository.existsByUser_IdAndTravelRecord_Id(
            authenticatedUser.id(), travelRecordId);
  }

  private List<TravelRecordFeedResDto> toFeedResponses(
      List<TravelRecord> travelRecords, AuthenticatedUser authenticatedUser) {
    if (travelRecords.isEmpty()) {
      return List.of();
    }

    UUID authenticatedUserId =
        authenticatedUser == null || authenticatedUser.id() == null ? null : authenticatedUser.id();
    if (authenticatedUserId == null) {
      return travelRecords.stream().map(TravelRecordFeedResDto::from).toList();
    }

    Set<UUID> likedTravelRecordIds =
        Set.copyOf(
            travelRecordLikeRepository.findLikedTravelRecordIds(
                authenticatedUserId,
                travelRecords.stream().map(TravelRecord::getId).toList()));

    return travelRecords.stream()
        .map(
            travelRecord ->
                TravelRecordFeedResDto.from(
                    travelRecord, likedTravelRecordIds.contains(travelRecord.getId())))
        .toList();
  }

  private TravelRecordDayResDto toDayResponse(TravelRecordDay day) {
    Map<UUID, TravelRecordRoute> routeByFromPlaceId =
        travelRecordRouteRepository.findByTravelRecordDay_Id(day.getId()).stream()
            .collect(Collectors.toMap(route -> route.getFromPlace().getId(), Function.identity()));

    return TravelRecordDayResDto.of(
        day,
        travelRecordPlaceRepository.findByTravelRecordDay_IdOrderBySequenceAsc(day.getId()),
        routeByFromPlaceId);
  }

  private PlaceReviewResDto toPlaceReviewResponse(PlaceReview placeReview) {
    return PlaceReviewResDto.from(placeReview, findPlaceReviewMediaUrls(placeReview.getId()));
  }

  private PlaceReviewSummaryResDto.PlaceReviewItemResDto toPlaceReviewSummaryItem(
      PlaceReview placeReview, List<String> mediaUrls) {
    TravelRecordPlace place = placeReview.getTravelRecordPlace();
    TravelRecord travelRecord = place.getTravelRecordDay().getTravelRecord();

    return new PlaceReviewSummaryResDto.PlaceReviewItemResDto(
        placeReview.getId(),
        travelRecord.getId(),
        travelRecord.getTitle(),
        travelRecord.getAuthor().getId(),
        travelRecord.getAuthor().getNickname(),
        place.getId(),
        place.getPlaceName(),
        placeReview.getRating(),
        placeReview.getStayMinutes(),
        placeReview.getContent(),
        List.copyOf(placeReview.getTags()),
        List.copyOf(mediaUrls),
        placeReview.getCreatedAt(),
        placeReview.getUpdatedAt());
  }

  private List<String> findPlaceReviewMediaUrls(UUID placeReviewId) {
    return placeReviewImageRepository.findByPlaceReview_IdOrderBySequenceAsc(placeReviewId).stream()
        .map(PlaceReviewImage::getUrl)
        .toList();
  }

  private void savePlaceReviewMedia(PlaceReview placeReview, List<String> mediaUrls) {
    placeReviewImageRepository.deleteByPlaceReview_Id(placeReview.getId());
    for (int index = 0; index < mediaUrls.size(); index++) {
      placeReviewImageRepository.save(
          PlaceReviewImage.builder()
              .placeReview(placeReview)
              .url(mediaUrls.get(index))
              .sequence(index + 1)
              .build());
    }
  }

  private User findAuthenticatedUser(AuthenticatedUser authenticatedUser) {
    if (authenticatedUser == null || authenticatedUser.id() == null) {
      throw new UnauthenticatedException();
    }

    return userRepository
        .findById(authenticatedUser.id())
        .orElseThrow(UnauthenticatedException::new);
  }

  private Travel findTravel(UUID travelId) {
    return travelRepository
        .findById(travelId)
        .orElseThrow(() -> new ResourceNotFoundException("Travel not found."));
  }

  private TravelRecord findTravelRecord(UUID travelRecordId) {
    return travelRecordRepository
        .findById(travelRecordId)
        .orElseThrow(() -> new ResourceNotFoundException("Travel record not found."));
  }

  private TravelRecordPlace findTravelRecordPlaceInRecord(
      UUID travelRecordPlaceId, UUID travelRecordId) {
    TravelRecordPlace travelRecordPlace =
        travelRecordPlaceRepository
            .findById(travelRecordPlaceId)
            .orElseThrow(() -> new ResourceNotFoundException("Travel record place not found."));

    if (!travelRecordPlace.getTravelRecordDay().getTravelRecord().getId().equals(travelRecordId)) {
      throw new ResourceNotFoundException("Travel record place not found.");
    }

    return travelRecordPlace;
  }

  private PlanPlace findPlanPlaceInTravel(UUID planPlaceId, UUID travelId) {
    PlanPlace planPlace =
        planPlaceRepository
            .findById(planPlaceId)
            .orElseThrow(() -> new ResourceNotFoundException("Plan place not found."));

    if (!planPlace.getPlan().getTravel().getId().equals(travelId)) {
      throw new ResourceNotFoundException("Plan place not found.");
    }

    return planPlace;
  }

  private PlaceReview findPlaceReviewByPlanPlaceId(UUID planPlaceId, UUID authorId) {
    return placeReviewRepository
        .findByPlanPlace_IdAndAuthor_Id(planPlaceId, authorId)
        .orElseThrow(() -> new ResourceNotFoundException("Place review not found."));
  }

  private PlaceReview findPlaceReviewByTravelRecordPlaceId(UUID travelRecordPlaceId) {
    return placeReviewRepository
        .findByTravelRecordPlace_Id(travelRecordPlaceId)
        .orElseThrow(() -> new ResourceNotFoundException("Place review not found."));
  }

  private TravelRecordComment findCommentInTravelRecord(UUID commentId, UUID travelRecordId) {
    return travelRecordCommentRepository
        .findByIdAndTravelRecord_Id(commentId, travelRecordId)
        .orElseThrow(() -> new ResourceNotFoundException("Travel record comment not found."));
  }

  private void validateTravelMember(UUID travelId, UUID userId) {
    if (!travelMemberRepository.existsByTravel_IdAndUser_Id(travelId, userId)) {
      throw new ForbiddenException("User is not a travel member.");
    }
  }

  private void validateDraftBelongsToTravel(TravelRecord travelRecord, UUID travelId) {
    if (travelRecord.getOriginalTravel() == null
        || !travelRecord.getOriginalTravel().getId().equals(travelId)) {
      throw new ResourceNotFoundException("Travel record not found.");
    }
  }

  private void validateAuthor(TravelRecord travelRecord, UUID userId) {
    if (!travelRecord.getAuthor().getId().equals(userId)) {
      throw new ForbiddenException("User is not the travel record author.");
    }
  }

  private void validateCommentAuthor(TravelRecordComment comment, UUID userId) {
    if (!comment.getAuthor().getId().equals(userId)) {
      throw new ForbiddenException("User is not the travel record comment author.");
    }
  }

  private void validateDraft(TravelRecord travelRecord) {
    if (travelRecord.getStatus() != TravelRecordStatus.DRAFT) {
      throw new IllegalArgumentException("Only draft travel records can be accessed here.");
    }
  }

  private void validatePublishable(TravelRecord travelRecord) {
    if (travelRecord.getTitle() == null || travelRecord.getTitle().isBlank()) {
      throw new IllegalArgumentException("Travel record title is required.");
    }

    if (travelRecordDayRepository
        .findByTravelRecord_IdOrderByDayNumberAsc(travelRecord.getId())
        .isEmpty()) {
      throw new IllegalArgumentException("Travel record itinerary is required.");
    }

    if (travelRecord.getOverallRating() == null) {
      throw new IllegalArgumentException("Travel record overall rating is required.");
    }
  }

  private void validatePublished(TravelRecord travelRecord) {
    if (travelRecord.getStatus() != TravelRecordStatus.PUBLISHED) {
      throw new ResourceNotFoundException("Travel record not found.");
    }
  }

  private void validateRepublishable(TravelRecord travelRecord) {
    if (travelRecord.getStatus() != TravelRecordStatus.HIDDEN) {
      throw new IllegalArgumentException("Only hidden travel records can be republished.");
    }

    if (travelRecord.getPublishedAt() == null) {
      throw new IllegalArgumentException("Only previously published travel records can be republished.");
    }
  }

  private void validateUpdateRequest(TravelRecordUpdateReqDto request) {
    if (request == null) {
      return;
    }

    if (request.title() != null && request.title().isBlank()) {
      throw new IllegalArgumentException("Travel record title cannot be blank.");
    }

    validateTravelRecordOverallRating(request.overallRating());
  }

  private void validateCreateRequest(TravelRecordCreateReqDto request) {
    if (request == null) {
      return;
    }

    if (request.title() != null && request.title().isBlank()) {
      throw new IllegalArgumentException("Travel record title cannot be blank.");
    }

    validateTravelRecordOverallRating(request.overallRating());
  }

  private void validateTravelRecordOverallRating(Integer overallRating) {
    if (overallRating == null) {
      return;
    }

    if (overallRating < 1 || overallRating > 5) {
      throw new IllegalArgumentException("Travel record overall rating must be between 1 and 5.");
    }
  }

  private void validateCommentCreateRequest(TravelRecordCommentCreateReqDto request) {
    if (request == null || request.content() == null || request.content().isBlank()) {
      throw new IllegalArgumentException("Travel record comment content is required.");
    }

    if (request.content().trim().length() > MAX_COMMENT_CONTENT_LENGTH) {
      throw new IllegalArgumentException(
          "Travel record comment content must be "
              + MAX_COMMENT_CONTENT_LENGTH
              + " characters or less.");
    }
  }

  private void validateCommentUpdateRequest(TravelRecordCommentUpdateReqDto request) {
    if (request == null || request.content() == null || request.content().isBlank()) {
      throw new IllegalArgumentException("Travel record comment content is required.");
    }

    if (request.content().trim().length() > MAX_COMMENT_CONTENT_LENGTH) {
      throw new IllegalArgumentException(
          "Travel record comment content must be "
              + MAX_COMMENT_CONTENT_LENGTH
              + " characters or less.");
    }
  }

  private void validatePlaceReviewCreateRequest(PlaceReviewCreateReqDto request) {
    if (request == null || request.rating() == null) {
      throw new IllegalArgumentException("Place review rating is required.");
    }

    if (request.rating() < 1 || request.rating() > 5) {
      throw new IllegalArgumentException("Place review rating must be between 1 and 5.");
    }

    validatePlaceReviewStayMinutes(request.stayMinutes());
  }

  private void validatePlaceReviewUpdateRequest(PlaceReviewUpdateReqDto request) {
    if (request == null) {
      return;
    }

    if (request.rating() != null && (request.rating() < 1 || request.rating() > 5)) {
      throw new IllegalArgumentException("Place review rating must be between 1 and 5.");
    }

    validatePlaceReviewStayMinutes(request.stayMinutes());
  }

  private void validatePlaceReviewStayMinutes(Integer stayMinutes) {
    if (stayMinutes == null) {
      return;
    }

    if (stayMinutes < 0) {
      throw new IllegalArgumentException("Stay minutes must be 0 or greater.");
    }
  }

  private List<String> normalizePlaceReviewTags(List<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return List.of();
    }

    List<String> normalizedTags =
        tags.stream()
            .filter(tag -> tag != null && !tag.isBlank())
            .map(String::trim)
            .distinct()
            .toList();

    if (normalizedTags.size() > MAX_PLACE_REVIEW_TAG_COUNT) {
      throw new IllegalArgumentException(
          "Place review tags must be " + MAX_PLACE_REVIEW_TAG_COUNT + " or fewer.");
    }

    boolean hasTooLongTag =
        normalizedTags.stream().anyMatch(tag -> tag.length() > MAX_PLACE_REVIEW_TAG_LENGTH);
    if (hasTooLongTag) {
      throw new IllegalArgumentException(
          "Place review tag must be "
              + MAX_PLACE_REVIEW_TAG_LENGTH
              + " characters or less.");
    }

    return normalizedTags;
  }

  private List<String> normalizePlaceReviewMediaUrls(List<String> mediaUrls) {
    if (mediaUrls == null || mediaUrls.isEmpty()) {
      return List.of();
    }

    List<String> normalizedMediaUrls =
        mediaUrls.stream()
            .filter(url -> url != null && !url.isBlank())
            .map(String::trim)
            .distinct()
            .toList();

    if (normalizedMediaUrls.size() > MAX_PLACE_REVIEW_MEDIA_COUNT) {
      throw new IllegalArgumentException(
          "Place review media URLs must be " + MAX_PLACE_REVIEW_MEDIA_COUNT + " or fewer.");
    }

    boolean hasTooLongUrl =
        normalizedMediaUrls.stream()
            .anyMatch(url -> url.length() > MAX_PLACE_REVIEW_MEDIA_URL_LENGTH);
    if (hasTooLongUrl) {
      throw new IllegalArgumentException(
          "Place review media URL must be "
              + MAX_PLACE_REVIEW_MEDIA_URL_LENGTH
              + " characters or less.");
    }

    return normalizedMediaUrls;
  }

  private void validatePlaceReviewSummaryRequest(PlaceProvider provider, String providerPlaceId) {
    if (provider == null) {
      throw new IllegalArgumentException("Place provider is required.");
    }

    validatePlaceId(providerPlaceId);
  }

  private void validatePlaceId(String placeId) {
    if (placeId == null || placeId.isBlank()) {
      throw new IllegalArgumentException("Place id is required.");
    }
  }

  private void validatePlaceReviewNotDuplicated(UUID planPlaceId, UUID authorId) {
    if (placeReviewRepository.findByPlanPlace_IdAndAuthor_Id(planPlaceId, authorId).isPresent()) {
      throw new DuplicateResourceException("Place review already exists.");
    }
  }

  private void validateBookmarkNotDuplicated(UUID userId, UUID travelRecordId) {
    if (travelRecordBookmarkRepository.existsByUser_IdAndTravelRecord_Id(userId, travelRecordId)) {
      throw new DuplicateResourceException("Travel record bookmark already exists.");
    }
  }

  private void validateLikeNotDuplicated(UUID userId, UUID travelRecordId) {
    if (travelRecordLikeRepository.existsByUser_IdAndTravelRecord_Id(userId, travelRecordId)) {
      throw new DuplicateResourceException("Travel record like already exists.");
    }
  }

  private void validateCompletedTravel(Travel travel) {
    if (travel.getStatus() != TravelStatus.COMPLETED) {
      throw new IllegalArgumentException("Only completed travels can be recorded.");
    }
  }

  private void validateNotDuplicated(UUID travelId, UUID authorId) {
    if (travelRecordRepository.existsByOriginalTravel_IdAndAuthor_Id(travelId, authorId)) {
      throw new DuplicateResourceException("Travel record already exists.");
    }
  }

  private String resolveTitle(Travel travel, TravelRecordCreateReqDto request) {
    if (request != null && request.title() != null && !request.title().isBlank()) {
      return request.title();
    }

    if (travel.getTitle() != null && !travel.getTitle().isBlank()) {
      return travel.getTitle();
    }

    return DEFAULT_TITLE;
  }

  private double calculateAverageRating(List<PlaceReview> reviews) {
    if (reviews.isEmpty()) {
      return 0.0;
    }

    double average =
        reviews.stream().mapToInt(PlaceReview::getRating).average().orElse(0.0);

    return Math.round(average * 10.0) / 10.0;
  }

  private Map<Integer, Long> calculateRatingCounts(List<PlaceReview> reviews) {
    Map<Integer, Long> ratingCounts = new LinkedHashMap<>();
    for (int rating = 1; rating <= 5; rating++) {
      ratingCounts.put(rating, 0L);
    }

    for (PlaceReview review : reviews) {
      ratingCounts.computeIfPresent(review.getRating(), (rating, count) -> count + 1);
    }

    return ratingCounts;
  }

  private int resolveFeedSize(Integer size) {
    if (size == null) {
      return DEFAULT_FEED_SIZE;
    }

    if (size < 1 || size > MAX_FEED_SIZE) {
      throw new IllegalArgumentException("Feed size must be between 1 and 50.");
    }

    return size;
  }

  private List<TravelRecord> findFeedRecords(
      FeedCursor feedCursor,
      FeedSearchCondition searchCondition,
      TravelRecordFeedSort sort,
      PageRequest pageRequest) {
    if (feedCursor == null) {
      return switch (sort) {
        case LATEST -> travelRecordRepository.findFeedPage(
            TravelRecordStatus.PUBLISHED,
            searchCondition.hasKeyword(),
            searchCondition.keywordPattern(),
            searchCondition.hasPlace(),
            searchCondition.placeId(),
            searchCondition.hasRegion(),
            searchCondition.regionPattern(),
            searchCondition.hasCity(),
            searchCondition.cityPattern(),
            searchCondition.hasTravelStartDate(),
            searchCondition.travelStartDate(),
            searchCondition.hasTravelEndDate(),
            searchCondition.travelEndDate(),
            pageRequest);
        case MOST_LIKED -> travelRecordRepository.findFeedPageOrderByLikeCount(
            TravelRecordStatus.PUBLISHED,
            searchCondition.hasKeyword(),
            searchCondition.keywordPattern(),
            searchCondition.hasPlace(),
            searchCondition.placeId(),
            searchCondition.hasRegion(),
            searchCondition.regionPattern(),
            searchCondition.hasCity(),
            searchCondition.cityPattern(),
            searchCondition.hasTravelStartDate(),
            searchCondition.travelStartDate(),
            searchCondition.hasTravelEndDate(),
            searchCondition.travelEndDate(),
            pageRequest);
        case MOST_VIEWED -> travelRecordRepository.findFeedPageOrderByViewCount(
            TravelRecordStatus.PUBLISHED,
            searchCondition.hasKeyword(),
            searchCondition.keywordPattern(),
            searchCondition.hasPlace(),
            searchCondition.placeId(),
            searchCondition.hasRegion(),
            searchCondition.regionPattern(),
            searchCondition.hasCity(),
            searchCondition.cityPattern(),
            searchCondition.hasTravelStartDate(),
            searchCondition.travelStartDate(),
            searchCondition.hasTravelEndDate(),
            searchCondition.travelEndDate(),
            pageRequest);
      };
    }

    return switch (sort) {
      case LATEST -> travelRecordRepository.findFeedPageAfterCursor(
          TravelRecordStatus.PUBLISHED,
          feedCursor.publishedAt(),
          feedCursor.createdAt(),
          searchCondition.hasKeyword(),
          searchCondition.keywordPattern(),
          searchCondition.hasPlace(),
          searchCondition.placeId(),
          searchCondition.hasRegion(),
          searchCondition.regionPattern(),
          searchCondition.hasCity(),
          searchCondition.cityPattern(),
          searchCondition.hasTravelStartDate(),
          searchCondition.travelStartDate(),
          searchCondition.hasTravelEndDate(),
          searchCondition.travelEndDate(),
          pageRequest);
      case MOST_LIKED -> travelRecordRepository.findFeedPageAfterCursorOrderByLikeCount(
          TravelRecordStatus.PUBLISHED,
          feedCursor.sortCount(),
          feedCursor.publishedAt(),
          feedCursor.createdAt(),
          searchCondition.hasKeyword(),
          searchCondition.keywordPattern(),
          searchCondition.hasPlace(),
          searchCondition.placeId(),
          searchCondition.hasRegion(),
          searchCondition.regionPattern(),
          searchCondition.hasCity(),
          searchCondition.cityPattern(),
          searchCondition.hasTravelStartDate(),
          searchCondition.travelStartDate(),
          searchCondition.hasTravelEndDate(),
          searchCondition.travelEndDate(),
          pageRequest);
      case MOST_VIEWED -> travelRecordRepository.findFeedPageAfterCursorOrderByViewCount(
          TravelRecordStatus.PUBLISHED,
          feedCursor.sortCount(),
          feedCursor.publishedAt(),
          feedCursor.createdAt(),
          searchCondition.hasKeyword(),
          searchCondition.keywordPattern(),
          searchCondition.hasPlace(),
          searchCondition.placeId(),
          searchCondition.hasRegion(),
          searchCondition.regionPattern(),
          searchCondition.hasCity(),
          searchCondition.cityPattern(),
          searchCondition.hasTravelStartDate(),
          searchCondition.travelStartDate(),
          searchCondition.hasTravelEndDate(),
          searchCondition.travelEndDate(),
          pageRequest);
    };
  }

  private FeedSearchCondition resolveFeedSearchCondition(
      String keyword,
      String placeId,
      LocalDate travelStartDate,
      LocalDate travelEndDate,
      String region,
      String city) {
    String normalizedKeyword =
        keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase();
    String normalizedRegion =
        region == null || region.isBlank() ? null : region.trim().toLowerCase();
    String normalizedCity = city == null || city.isBlank() ? null : city.trim().toLowerCase();
    boolean hasKeyword = normalizedKeyword != null;
    boolean hasRegion = normalizedRegion != null;
    boolean hasCity = normalizedCity != null;
    String normalizedPlaceId = placeId == null || placeId.isBlank() ? null : placeId.trim();
    boolean hasPlace = normalizedPlaceId != null;

    if (travelStartDate != null
        && travelEndDate != null
        && travelEndDate.isBefore(travelStartDate)) {
      throw new IllegalArgumentException("Travel end date cannot be before travel start date.");
    }

    return new FeedSearchCondition(
        hasKeyword,
        hasKeyword ? "%" + normalizedKeyword + "%" : "",
        hasPlace,
        hasPlace ? normalizedPlaceId : "",
        hasRegion,
        hasRegion ? "%" + normalizedRegion + "%" : "",
        hasCity,
        hasCity ? "%" + normalizedCity + "%" : "",
        travelStartDate != null,
        travelStartDate,
        travelEndDate != null,
        travelEndDate);
  }

  private void validateFeedCursorSort(FeedCursor feedCursor, TravelRecordFeedSort sort) {
    if (feedCursor != null && feedCursor.sort() != sort) {
      throw new IllegalArgumentException("Feed cursor sort does not match requested sort.");
    }
  }

  private String encodeFeedCursor(TravelRecord travelRecord, TravelRecordFeedSort sort) {
    String rawCursor =
        sort
            + "|"
            + resolveCursorSortCount(travelRecord, sort)
            + "|"
            + travelRecord.getPublishedAt()
            + "|"
            + travelRecord.getCreatedAt();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(rawCursor.getBytes(StandardCharsets.UTF_8));
  }

  private long resolveCursorSortCount(TravelRecord travelRecord, TravelRecordFeedSort sort) {
    return switch (sort) {
      case LATEST -> 0L;
      case MOST_LIKED -> travelRecord.getLikeCount();
      case MOST_VIEWED -> travelRecord.getViewCount();
    };
  }

  private FeedCursor decodeFeedCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }

    try {
      String rawCursor =
          new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] values = rawCursor.split("\\|");
      if (values.length == 2) {
        return new FeedCursor(
            TravelRecordFeedSort.LATEST,
            0L,
            LocalDateTime.parse(values[0]),
            LocalDateTime.parse(values[1]));
      }

      if (values.length != 4) {
        throw new IllegalArgumentException("Invalid feed cursor.");
      }

      return new FeedCursor(
          TravelRecordFeedSort.valueOf(values[0]),
          Long.parseLong(values[1]),
          LocalDateTime.parse(values[2]),
          LocalDateTime.parse(values[3]));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Invalid feed cursor.");
    }
  }

  private record FeedCursor(
      TravelRecordFeedSort sort,
      long sortCount,
      LocalDateTime publishedAt,
      LocalDateTime createdAt) {}

  private record FeedSearchCondition(
      boolean hasKeyword,
      String keywordPattern,
      boolean hasPlace,
      String placeId,
      boolean hasRegion,
      String regionPattern,
      boolean hasCity,
      String cityPattern,
      boolean hasTravelStartDate,
      LocalDate travelStartDate,
      boolean hasTravelEndDate,
      LocalDate travelEndDate) {}
}
