package com.butingbe.domain.travelrecord.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.file.service.FileStorageService;
import com.butingbe.domain.travel.dto.response.TravelPlansResDto;
import com.butingbe.domain.travel.dto.response.TravelPlansResDto.PlanDayResDto;
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
import com.butingbe.domain.travelrecord.dto.response.TravelRecordResDto.TravelRecordDayResDto;
import com.butingbe.domain.travelrecord.entity.TravelRecord;
import com.butingbe.domain.travelrecord.entity.TravelRecordBookmark;
import com.butingbe.domain.travelrecord.entity.TravelRecordComment;
import com.butingbe.domain.travelrecord.entity.TravelRecordDay;
import com.butingbe.domain.travelrecord.entity.TravelRecordImage;
import com.butingbe.domain.travelrecord.entity.TravelRecordLike;
import com.butingbe.domain.travelrecord.entity.TravelRecordPlace;
import com.butingbe.domain.travelrecord.entity.TravelRecordRoute;
import com.butingbe.domain.travelrecord.entity.TravelRecordStatus;
import com.butingbe.domain.travelrecord.repository.TravelRecordBookmarkRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordCommentRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordDayRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordFeedSpecifications;
import com.butingbe.domain.travelrecord.repository.TravelRecordImageRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordLikeRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordPlaceRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordRouteRepository;
import com.butingbe.domain.travelteam.entity.TravelMember;
import com.butingbe.domain.travelteam.entity.TravelTeamRole;
import com.butingbe.domain.travelteam.repository.TravelMemberRepository;
import com.butingbe.domain.user.entity.User;
import com.butingbe.global.error.exception.DuplicateResourceException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.InvalidRequestException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
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
  private static final int MAX_TRAVEL_TITLE_LENGTH = 15;
  private static final int MAX_COMMENT_CONTENT_LENGTH = 1000;
  private static final int MAX_TRAVEL_RECORD_IMAGE_COUNT = 20;
  private static final int MAX_TRAVEL_RECORD_IMAGE_URL_LENGTH = 1000;

  private final TravelRepository travelRepository;
  private final PlanRepository planRepository;
  private final PlanPlaceRepository planPlaceRepository;
  private final PlanRouteRepository planRouteRepository;
  private final TravelMemberRepository travelMemberRepository;
  private final TravelRecordRepository travelRecordRepository;
  private final TravelRecordDayRepository travelRecordDayRepository;
  private final TravelRecordImageRepository travelRecordImageRepository;
  private final TravelRecordPlaceRepository travelRecordPlaceRepository;
  private final TravelRecordRouteRepository travelRecordRouteRepository;
  private final FileStorageService fileStorageService;
  private final TravelRecordSupport support;
  private final PlaceReviewService placeReviewService;
  private final TravelRecordBookmarkRepository travelRecordBookmarkRepository;
  private final TravelRecordLikeRepository travelRecordLikeRepository;
  private final TravelRecordCommentRepository travelRecordCommentRepository;

  // 장소 리뷰 로직은 PlaceReviewService 로 옮겼다. 인터페이스는 네 조각을 모두 뗀 뒤 한 번에 정리한다.
  // 조각마다 컨트롤러와 테스트를 손대면 같은 파일을 네 번 건드리게 된다.
  @Override
  public PlaceReviewSummaryResDto getPlaceReviewSummary(String placeId) {
    return placeReviewService.getPlaceReviewSummary(placeId);
  }

  @Override
  public PlaceReviewSummaryResDto getPlaceReviewSummary(
      PlaceProvider provider, String providerPlaceId) {
    return placeReviewService.getPlaceReviewSummary(provider, providerPlaceId);
  }

  @Override
  public PlaceReviewResDto createPlaceReview(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      UUID planPlaceId,
      PlaceReviewCreateReqDto request) {
    return placeReviewService.createPlaceReview(authenticatedUser, travelId, planPlaceId, request);
  }

  @Override
  public PlaceReviewResDto getPlaceReview(
      AuthenticatedUser authenticatedUser, UUID travelId, UUID planPlaceId) {
    return placeReviewService.getPlaceReview(authenticatedUser, travelId, planPlaceId);
  }

  @Override
  public PlaceReviewResDto updatePlaceReview(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      UUID planPlaceId,
      PlaceReviewUpdateReqDto request) {
    return placeReviewService.updatePlaceReview(authenticatedUser, travelId, planPlaceId, request);
  }

  @Override
  public void deletePlaceReview(
      AuthenticatedUser authenticatedUser, UUID travelId, UUID planPlaceId) {
    placeReviewService.deletePlaceReview(authenticatedUser, travelId, planPlaceId);
  }

  @Override
  @Transactional
  public TravelRecordResDto createDraft(
      AuthenticatedUser authenticatedUser, UUID travelId, TravelRecordCreateReqDto request) {
    User author = support.findAuthenticatedUser(authenticatedUser);
    Travel travel = findTravel(travelId);
    support.validateTravelMember(travelId, author.getId());
    validateCompletedTravel(travel);
    validateCreateRequest(request);
    validateNotDuplicated(travelId, author.getId());
    List<String> imageUrls =
        request == null ? List.of() : normalizeTravelRecordImageUrls(request.imageUrls());
    String coverImageUrl =
        resolveCoverImageUrl(request == null ? null : request.coverImageUrl(), imageUrls);

    TravelRecord travelRecord =
        travelRecordRepository.save(
            TravelRecord.builder()
                .originalTravel(travel)
                .author(author)
                .title(resolveTitle(travel, request))
                .content(request == null ? null : request.content())
                .coverImageUrl(coverImageUrl)
                .overallRating(request == null ? null : request.overallRating())
                .travelStartDate(travel.getStartDate())
                .travelEndDate(travel.getEndDate())
                .status(TravelRecordStatus.DRAFT)
                .build());

    saveTravelRecordImages(travelRecord, imageUrls);

    copyItinerarySnapshot(travelId, travelRecord);

    return toResponse(travelRecord);
  }

  @Override
  public TravelRecordResDto getDraft(
      AuthenticatedUser authenticatedUser, UUID travelId, UUID travelRecordId) {
    User author = support.findAuthenticatedUser(authenticatedUser);
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
    User author = support.findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validateDraftBelongsToTravel(travelRecord, travelId);
    validateAuthor(travelRecord, author.getId());
    validateDraft(travelRecord);
    validateUpdateRequest(request);

    if (request == null) {
      return toResponse(travelRecord);
    }

    List<String> imageUrls =
        request.imageUrls() == null ? null : normalizeTravelRecordImageUrls(request.imageUrls());
    travelRecord.updateContent(
        request.title(), request.content(), request.coverImageUrl(), request.overallRating());
    if (imageUrls != null) {
      travelRecord.updateCoverImageUrl(resolveCoverImageUrl(request.coverImageUrl(), imageUrls));
      saveTravelRecordImages(travelRecord, imageUrls);
    }

    return toResponse(travelRecord);
  }

  @Override
  @Transactional
  public TravelRecordResDto publish(
      AuthenticatedUser authenticatedUser, UUID travelId, UUID travelRecordId) {
    User author = support.findAuthenticatedUser(authenticatedUser);
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
    travelRecordRepository.increaseViewCount(travelRecordId);

    // 벌크 update 는 영속성 컨텍스트를 거치지 않는다. 위에서 읽은 엔티티는 증가 전 조회수를 들고
    // 있으므로 다시 읽는다(increaseViewCount 가 컨텍스트를 비운다).
    return toResponse(
        findTravelRecord(travelRecordId), isLikedBy(authenticatedUser, travelRecordId));
  }

  @Override
  @Transactional
  public TravelPlansResDto cloneToTravel(
      AuthenticatedUser authenticatedUser,
      UUID travelRecordId,
      TravelRecordCloneToTravelReqDto request) {
    User user = support.findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validatePublished(travelRecord);
    validateCloneToTravelRequest(request);

    List<TravelRecordDay> recordDays =
        travelRecordDayRepository.findByTravelRecord_IdOrderByDayNumberAsc(travelRecordId);
    validateCloneableItinerary(recordDays);

    LocalDate endDate = request.startDate().plusDays(recordDays.getLast().getDayNumber() - 1L);
    Travel travel =
        travelRepository.save(
            Travel.builder()
                .title(resolveCloneTitle(travelRecord, request))
                .startDate(request.startDate())
                .endDate(endDate)
                .status(TravelStatus.PLANNED)
                .hasHeavyBaggage(request.hasHeavyBaggage())
                .hasPets(request.hasPets())
                .travelStyle(request.travelStyle())
                .preferFlatTerrain(request.preferFlatTerrain())
                .pace(request.pace())
                .companionCount(request.companionCount())
                .preferredFoods(request.preferredFoods())
                .companionTypes(request.companionType())
                .accommodationArea(request.accommodationArea())
                .build());

    travelMemberRepository.save(
        TravelMember.builder().travel(travel).user(user).role(TravelTeamRole.LEADER).build());

    copyRecordItineraryToTravel(recordDays, travel, request.startDate());

    return toTravelPlansResponse(travel);
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
        resolveFeedSearchCondition(keyword, placeId, travelStartDate, travelEndDate, region, city);
    List<TravelRecord> fetchedRecords =
        findFeedRecords(feedCursor, searchCondition, feedSort, pageSize + 1);
    boolean hasNext = fetchedRecords.size() > pageSize;
    List<TravelRecord> pageRecords = hasNext ? fetchedRecords.subList(0, pageSize) : fetchedRecords;
    List<TravelRecordFeedResDto> items = toFeedResponses(pageRecords, authenticatedUser);

    return new TravelRecordFeedPageResDto(
        items, hasNext ? encodeFeedCursor(pageRecords.getLast(), feedSort) : null, hasNext);
  }

  @Override
  public List<TravelRecordManageResDto> getMyRecords(AuthenticatedUser authenticatedUser) {
    User author = support.findAuthenticatedUser(authenticatedUser);

    return travelRecordRepository.findByAuthor_IdOrderByCreatedAtDesc(author.getId()).stream()
        .map(
            travelRecord ->
                TravelRecordManageResDto.from(
                    travelRecord, toTravelRecordImageUrl(travelRecord.getCoverImageUrl())))
        .toList();
  }

  @Override
  public TravelRecordResDto getMyRecord(AuthenticatedUser authenticatedUser, UUID travelRecordId) {
    User author = support.findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validateAuthor(travelRecord, author.getId());

    return toResponse(travelRecord, isLikedBy(authenticatedUser, travelRecord.getId()));
  }

  @Override
  @Transactional
  public TravelRecordResDto updateMyRecord(
      AuthenticatedUser authenticatedUser, UUID travelRecordId, TravelRecordUpdateReqDto request) {
    User author = support.findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validateAuthor(travelRecord, author.getId());
    validateUpdateRequest(request);

    if (request == null) {
      return toResponse(travelRecord);
    }

    List<String> imageUrls =
        request.imageUrls() == null ? null : normalizeTravelRecordImageUrls(request.imageUrls());
    travelRecord.updateContent(
        request.title(), request.content(), request.coverImageUrl(), request.overallRating());
    if (imageUrls != null) {
      travelRecord.updateCoverImageUrl(resolveCoverImageUrl(request.coverImageUrl(), imageUrls));
      saveTravelRecordImages(travelRecord, imageUrls);
    }

    return toResponse(travelRecord);
  }

  @Override
  @Transactional
  public TravelRecordResDto hideMyRecord(AuthenticatedUser authenticatedUser, UUID travelRecordId) {
    User author = support.findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validateAuthor(travelRecord, author.getId());

    travelRecord.hide();

    return toResponse(travelRecord);
  }

  @Override
  @Transactional
  public TravelRecordResDto republishMyRecord(
      AuthenticatedUser authenticatedUser, UUID travelRecordId) {
    User author = support.findAuthenticatedUser(authenticatedUser);
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
    User user = support.findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validatePublished(travelRecord);
    validateBookmarkNotDuplicated(user.getId(), travelRecordId);

    TravelRecordBookmark bookmark =
        travelRecordBookmarkRepository.saveAndFlush(
            TravelRecordBookmark.builder().user(user).travelRecord(travelRecord).build());

    return TravelRecordBookmarkResDto.from(
        bookmark, toTravelRecordImageUrl(bookmark.getTravelRecord().getCoverImageUrl()));
  }

  @Override
  @Transactional
  public void unbookmarkTravelRecord(AuthenticatedUser authenticatedUser, UUID travelRecordId) {
    User user = support.findAuthenticatedUser(authenticatedUser);

    travelRecordBookmarkRepository
        .findByUser_IdAndTravelRecord_Id(user.getId(), travelRecordId)
        .ifPresent(travelRecordBookmarkRepository::delete);
  }

  @Override
  public List<TravelRecordBookmarkResDto> getMyBookmarkedRecords(
      AuthenticatedUser authenticatedUser) {
    User user = support.findAuthenticatedUser(authenticatedUser);

    return travelRecordBookmarkRepository
        .findByUser_IdAndTravelRecord_StatusOrderByCreatedAtDesc(
            user.getId(), TravelRecordStatus.PUBLISHED)
        .stream()
        .map(
            bookmark ->
                TravelRecordBookmarkResDto.from(
                    bookmark,
                    toTravelRecordImageUrl(bookmark.getTravelRecord().getCoverImageUrl())))
        .toList();
  }

  @Override
  @Transactional
  public TravelRecordLikeResDto likeTravelRecord(
      AuthenticatedUser authenticatedUser, UUID travelRecordId) {
    User user = support.findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validatePublished(travelRecord);
    validateLikeNotDuplicated(user.getId(), travelRecordId);

    TravelRecordLike like =
        travelRecordLikeRepository.saveAndFlush(
            TravelRecordLike.builder().user(user).travelRecord(travelRecord).build());
    travelRecordRepository.increaseLikeCount(travelRecordId);

    return TravelRecordLikeResDto.from(
        like, travelRecordRepository.findLikeCount(travelRecordId).orElse(0L));
  }

  @Override
  @Transactional
  public void unlikeTravelRecord(AuthenticatedUser authenticatedUser, UUID travelRecordId) {
    User user = support.findAuthenticatedUser(authenticatedUser);

    travelRecordLikeRepository
        .findByUser_IdAndTravelRecord_Id(user.getId(), travelRecordId)
        .ifPresent(
            like -> {
              travelRecordLikeRepository.delete(like);
              travelRecordRepository.decreaseLikeCount(travelRecordId);
            });
  }

  @Override
  @Transactional
  public TravelRecordCommentResDto createComment(
      AuthenticatedUser authenticatedUser,
      UUID travelRecordId,
      TravelRecordCommentCreateReqDto request) {
    User author = support.findAuthenticatedUser(authenticatedUser);
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
    User author = support.findAuthenticatedUser(authenticatedUser);
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
    User author = support.findAuthenticatedUser(authenticatedUser);
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
      AuthenticatedUser authenticatedUser, String placeId, String cursor, Integer size) {
    support.validatePlaceId(placeId);
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
    List<TravelRecord> pageRecords = hasNext ? fetchedRecords.subList(0, pageSize) : fetchedRecords;
    List<TravelRecordFeedResDto> items = toFeedResponses(pageRecords, authenticatedUser);

    return new TravelRecordFeedPageResDto(
        items,
        hasNext ? encodeFeedCursor(pageRecords.getLast(), TravelRecordFeedSort.LATEST) : null,
        hasNext);
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
      placeReviewService.copyPlaceReviewSnapshot(place, recordPlace);
    }

    return copiedPlaceByOriginalId;
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

  private void copyRecordItineraryToTravel(
      List<TravelRecordDay> recordDays, Travel travel, LocalDate startDate) {
    for (TravelRecordDay recordDay : recordDays) {
      Plan plan =
          planRepository.save(
              Plan.builder()
                  .travel(travel)
                  .dayNumber(recordDay.getDayNumber())
                  .visitDate(startDate.plusDays(recordDay.getDayNumber() - 1L))
                  .build());

      Map<UUID, PlanPlace> copiedPlaceByRecordPlaceId = copyRecordPlaces(recordDay, plan);
      copyRecordRoutes(recordDay, plan, copiedPlaceByRecordPlaceId);
    }
  }

  private Map<UUID, PlanPlace> copyRecordPlaces(TravelRecordDay recordDay, Plan plan) {
    Map<UUID, PlanPlace> copiedPlaceByRecordPlaceId = new HashMap<>();

    for (TravelRecordPlace recordPlace :
        travelRecordPlaceRepository.findByTravelRecordDay_IdOrderBySequenceAsc(recordDay.getId())) {
      PlanPlace planPlace =
          planPlaceRepository.save(
              PlanPlace.builder()
                  .plan(plan)
                  .sequence(recordPlace.getSequence())
                  .placeName(recordPlace.getPlaceName())
                  .address(recordPlace.getAddress())
                  .latitude(recordPlace.getLatitude())
                  .longitude(recordPlace.getLongitude())
                  .provider(recordPlace.getProvider())
                  .providerPlaceId(recordPlace.getProviderPlaceId())
                  .durationMinutes(recordPlace.getDurationMinutes())
                  .memo(recordPlace.getMemo())
                  .scheduledTime(recordPlace.getScheduledTime())
                  .visited(false)
                  .build());

      copiedPlaceByRecordPlaceId.put(recordPlace.getId(), planPlace);
    }

    return copiedPlaceByRecordPlaceId;
  }

  private void copyRecordRoutes(
      TravelRecordDay recordDay, Plan plan, Map<UUID, PlanPlace> copiedPlaceByRecordPlaceId) {
    for (TravelRecordRoute recordRoute :
        travelRecordRouteRepository.findByTravelRecordDay_Id(recordDay.getId())) {
      PlanPlace fromPlace = copiedPlaceByRecordPlaceId.get(recordRoute.getFromPlace().getId());
      PlanPlace toPlace = copiedPlaceByRecordPlaceId.get(recordRoute.getToPlace().getId());

      if (fromPlace == null || toPlace == null) {
        continue;
      }

      planRouteRepository.save(
          PlanRoute.builder()
              .plan(plan)
              .fromPlace(fromPlace)
              .toPlace(toPlace)
              .transportType(recordRoute.getTransportType())
              .durationMinutes(recordRoute.getDurationMinutes())
              .distanceMeters(recordRoute.getDistanceMeters())
              .provider(recordRoute.getProvider())
              .calculatedAt(recordRoute.getCalculatedAt())
              .build());
    }
  }

  private TravelPlansResDto toTravelPlansResponse(Travel travel) {
    List<PlanDayResDto> days =
        planRepository.findByTravel_IdOrderByDayNumberAsc(travel.getId()).stream()
            .map(this::toPlanDayResponse)
            .toList();

    return TravelPlansResDto.of(travel, days);
  }

  private PlanDayResDto toPlanDayResponse(Plan plan) {
    Map<UUID, PlanRoute> routeByFromPlaceId =
        planRouteRepository.findByPlan_Id(plan.getId()).stream()
            .collect(Collectors.toMap(route -> route.getFromPlace().getId(), Function.identity()));

    return PlanDayResDto.of(
        plan,
        planPlaceRepository.findByPlan_IdOrderBySequenceAsc(plan.getId()),
        routeByFromPlaceId);
  }

  private TravelRecordResDto toResponse(TravelRecord travelRecord) {
    return toResponse(travelRecord, false);
  }

  private TravelRecordResDto toResponse(TravelRecord travelRecord, boolean likedByMe) {
    List<TravelRecordDay> travelRecordDays =
        travelRecordDayRepository.findByTravelRecord_IdOrderByDayNumberAsc(travelRecord.getId());
    List<TravelRecordDayResDto> days = toDayResponses(travelRecordDays);

    return TravelRecordResDto.of(
        travelRecord,
        days,
        toTravelRecordImageUrl(travelRecord.getCoverImageUrl()),
        findTravelRecordImageUrls(travelRecord.getId()),
        likedByMe);
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
      return travelRecords.stream()
          .map(travelRecord -> toFeedResponse(travelRecord, false))
          .toList();
    }

    Set<UUID> likedTravelRecordIds =
        Set.copyOf(
            travelRecordLikeRepository.findLikedTravelRecordIds(
                authenticatedUserId, travelRecords.stream().map(TravelRecord::getId).toList()));

    return travelRecords.stream()
        .map(
            travelRecord ->
                toFeedResponse(travelRecord, likedTravelRecordIds.contains(travelRecord.getId())))
        .toList();
  }

  private TravelRecordFeedResDto toFeedResponse(TravelRecord travelRecord, boolean likedByMe) {
    return TravelRecordFeedResDto.from(
        travelRecord, toTravelRecordImageUrl(travelRecord.getCoverImageUrl()), likedByMe);
  }

  /** 일자별로 장소와 경로를 따로 조회하면 5일짜리 기록 하나에 10번의 추가 쿼리가 나간다. 전체 일자 id로 두 번만 조회하고 메모리에서 묶는다. */
  private List<TravelRecordDayResDto> toDayResponses(List<TravelRecordDay> days) {
    if (days.isEmpty()) {
      return List.of();
    }

    List<UUID> dayIds = days.stream().map(TravelRecordDay::getId).toList();
    Map<UUID, List<TravelRecordPlace>> placesByDayId =
        travelRecordPlaceRepository.findByTravelRecordDay_IdInOrderBySequenceAsc(dayIds).stream()
            .collect(
                Collectors.groupingBy(
                    place -> place.getTravelRecordDay().getId(),
                    LinkedHashMap::new,
                    Collectors.toList()));
    Map<UUID, Map<UUID, TravelRecordRoute>> routesByDayId =
        travelRecordRouteRepository.findByTravelRecordDay_IdIn(dayIds).stream()
            .collect(
                Collectors.groupingBy(
                    route -> route.getTravelRecordDay().getId(),
                    Collectors.toMap(route -> route.getFromPlace().getId(), Function.identity())));

    return days.stream()
        .map(
            day ->
                TravelRecordDayResDto.of(
                    day,
                    placesByDayId.getOrDefault(day.getId(), List.of()),
                    routesByDayId.getOrDefault(day.getId(), Map.of())))
        .toList();
  }

  private List<String> findTravelRecordImageUrls(UUID travelRecordId) {
    return travelRecordImageRepository
        .findByTravelRecord_IdOrderBySequenceAsc(travelRecordId)
        .stream()
        .map(TravelRecordImage::getUrl)
        .map(this::toTravelRecordImageUrl)
        .toList();
  }

  private String toTravelRecordImageUrl(String storedUrl) {
    if (storedUrl == null || storedUrl.isBlank()) {
      return storedUrl;
    }

    String trimmedUrl = storedUrl.trim();
    String fileKey = extractS3FileKey(trimmedUrl);
    if (fileKey == null) {
      return trimmedUrl;
    }

    return fileStorageService.getPresignedUrl(fileKey);
  }

  private String extractS3FileKey(String imageUrl) {
    if (imageUrl.startsWith("uploads/")) {
      return imageUrl;
    }

    try {
      URI uri = new URI(imageUrl);
      String host = uri.getHost();
      if (host == null || !host.contains("amazonaws.com")) {
        return null;
      }

      String path = uri.getPath();
      if (path == null || path.isBlank()) {
        return null;
      }

      String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
      int uploadPrefixIndex = normalizedPath.indexOf("uploads/");
      return uploadPrefixIndex < 0 ? null : normalizedPath.substring(uploadPrefixIndex);
    } catch (URISyntaxException exception) {
      return null;
    }
  }

  private void saveTravelRecordImages(TravelRecord travelRecord, List<String> imageUrls) {
    travelRecordImageRepository.deleteByTravelRecord_Id(travelRecord.getId());
    travelRecordImageRepository.flush();
    for (int index = 0; index < imageUrls.size(); index++) {
      travelRecordImageRepository.save(
          TravelRecordImage.builder()
              .travelRecord(travelRecord)
              .url(imageUrls.get(index))
              .sequence(index + 1)
              .build());
    }
  }

  private String resolveCoverImageUrl(String coverImageUrl, List<String> imageUrls) {
    if (coverImageUrl != null && !coverImageUrl.isBlank()) {
      return coverImageUrl.trim();
    }

    if (imageUrls != null && !imageUrls.isEmpty()) {
      return imageUrls.getFirst();
    }

    return null;
  }

  private Travel findTravel(UUID travelId) {
    return travelRepository
        .findById(travelId)
        .orElseThrow(() -> new ResourceNotFoundException("error.travel.not_found"));
  }

  private TravelRecord findTravelRecord(UUID travelRecordId) {
    return travelRecordRepository
        .findById(travelRecordId)
        .orElseThrow(() -> new ResourceNotFoundException("error.travel_record.not_found"));
  }

  private TravelRecordComment findCommentInTravelRecord(UUID commentId, UUID travelRecordId) {
    return travelRecordCommentRepository
        .findByIdAndTravelRecord_Id(commentId, travelRecordId)
        .orElseThrow(() -> new ResourceNotFoundException("error.travel_record.comment.not_found"));
  }

  private void validateDraftBelongsToTravel(TravelRecord travelRecord, UUID travelId) {
    if (travelRecord.getOriginalTravel() == null
        || !travelRecord.getOriginalTravel().getId().equals(travelId)) {
      throw new ResourceNotFoundException("error.travel_record.not_found");
    }
  }

  private void validateAuthor(TravelRecord travelRecord, UUID userId) {
    if (!travelRecord.getAuthor().getId().equals(userId)) {
      throw new ForbiddenException("error.travel_record.not_author");
    }
  }

  private void validateCommentAuthor(TravelRecordComment comment, UUID userId) {
    if (!comment.getAuthor().getId().equals(userId)) {
      throw new ForbiddenException("error.travel_record.comment.not_author");
    }
  }

  private void validateDraft(TravelRecord travelRecord) {
    if (travelRecord.getStatus() != TravelRecordStatus.DRAFT) {
      throw new InvalidRequestException("error.travel_record.draft_only");
    }
  }

  private void validatePublishable(TravelRecord travelRecord) {
    if (travelRecord.getTitle() == null || travelRecord.getTitle().isBlank()) {
      throw new InvalidRequestException("error.travel_record.title_required");
    }

    if (travelRecordDayRepository
        .findByTravelRecord_IdOrderByDayNumberAsc(travelRecord.getId())
        .isEmpty()) {
      throw new InvalidRequestException("error.travel_record.itinerary_required");
    }

    if (travelRecord.getOverallRating() == null) {
      throw new InvalidRequestException("error.travel_record.rating_required");
    }
  }

  private void validatePublished(TravelRecord travelRecord) {
    if (travelRecord.getStatus() != TravelRecordStatus.PUBLISHED) {
      throw new ResourceNotFoundException("error.travel_record.not_found");
    }
  }

  private void validateRepublishable(TravelRecord travelRecord) {
    if (travelRecord.getStatus() != TravelRecordStatus.HIDDEN) {
      throw new InvalidRequestException("error.travel_record.republish_hidden_only");
    }

    if (travelRecord.getPublishedAt() == null) {
      throw new InvalidRequestException(
          "Only previously published travel records can be republished.");
    }
  }

  private void validateUpdateRequest(TravelRecordUpdateReqDto request) {
    if (request == null) {
      return;
    }

    if (request.title() != null && request.title().isBlank()) {
      throw new InvalidRequestException("error.travel_record.title_blank");
    }

    validateTravelRecordOverallRating(request.overallRating());
    normalizeTravelRecordImageUrls(request.imageUrls());
  }

  private void validateCreateRequest(TravelRecordCreateReqDto request) {
    if (request == null) {
      return;
    }

    if (request.title() != null && request.title().isBlank()) {
      throw new InvalidRequestException("error.travel_record.title_blank");
    }

    validateTravelRecordOverallRating(request.overallRating());
    normalizeTravelRecordImageUrls(request.imageUrls());
  }

  private void validateCloneToTravelRequest(TravelRecordCloneToTravelReqDto request) {
    if (request == null) {
      throw new InvalidRequestException("error.travel_record.clone_request_required");
    }

    if (request.startDate() == null) {
      throw new InvalidRequestException("error.travel.start_date_required");
    }

    if (request.title() != null && request.title().isBlank()) {
      throw new InvalidRequestException("error.travel.title_blank");
    }

    if (request.title() != null && request.title().length() > MAX_TRAVEL_TITLE_LENGTH) {
      throw new InvalidRequestException("error.travel.title_too_long");
    }
  }

  private void validateCloneableItinerary(List<TravelRecordDay> recordDays) {
    if (recordDays.isEmpty()) {
      throw new InvalidRequestException("error.travel_record.itinerary_required");
    }
  }

  private void validateTravelRecordOverallRating(Integer overallRating) {
    if (overallRating == null) {
      return;
    }

    if (overallRating < 1 || overallRating > 5) {
      throw new InvalidRequestException("error.travel_record.rating_range");
    }
  }

  private void validateCommentCreateRequest(TravelRecordCommentCreateReqDto request) {
    if (request == null || request.content() == null || request.content().isBlank()) {
      throw new InvalidRequestException("error.travel_record.comment.content_required");
    }

    if (request.content().trim().length() > MAX_COMMENT_CONTENT_LENGTH) {
      throw new InvalidRequestException(
          "Travel record comment content must be "
              + MAX_COMMENT_CONTENT_LENGTH
              + " characters or less.");
    }
  }

  private void validateCommentUpdateRequest(TravelRecordCommentUpdateReqDto request) {
    if (request == null || request.content() == null || request.content().isBlank()) {
      throw new InvalidRequestException("error.travel_record.comment.content_required");
    }

    if (request.content().trim().length() > MAX_COMMENT_CONTENT_LENGTH) {
      throw new InvalidRequestException(
          "Travel record comment content must be "
              + MAX_COMMENT_CONTENT_LENGTH
              + " characters or less.");
    }
  }

  private List<String> normalizeTravelRecordImageUrls(List<String> imageUrls) {
    if (imageUrls == null || imageUrls.isEmpty()) {
      return List.of();
    }

    List<String> normalizedImageUrls =
        imageUrls.stream()
            .filter(imageUrl -> imageUrl != null && !imageUrl.isBlank())
            .map(String::trim)
            .distinct()
            .toList();

    if (normalizedImageUrls.size() > MAX_TRAVEL_RECORD_IMAGE_COUNT) {
      throw new InvalidRequestException(
          "Travel record image URLs must be " + MAX_TRAVEL_RECORD_IMAGE_COUNT + " or fewer.");
    }

    boolean hasTooLongImageUrl =
        normalizedImageUrls.stream()
            .anyMatch(imageUrl -> imageUrl.length() > MAX_TRAVEL_RECORD_IMAGE_URL_LENGTH);
    if (hasTooLongImageUrl) {
      throw new InvalidRequestException(
          "Travel record image URL must be "
              + MAX_TRAVEL_RECORD_IMAGE_URL_LENGTH
              + " characters or less.");
    }

    return normalizedImageUrls;
  }

  private void validateBookmarkNotDuplicated(UUID userId, UUID travelRecordId) {
    if (travelRecordBookmarkRepository.existsByUser_IdAndTravelRecord_Id(userId, travelRecordId)) {
      throw new DuplicateResourceException("error.travel_record.bookmark.duplicate");
    }
  }

  private void validateLikeNotDuplicated(UUID userId, UUID travelRecordId) {
    if (travelRecordLikeRepository.existsByUser_IdAndTravelRecord_Id(userId, travelRecordId)) {
      throw new DuplicateResourceException("error.travel_record.like.duplicate");
    }
  }

  private void validateCompletedTravel(Travel travel) {
    if (travel.getStatus() != TravelStatus.COMPLETED) {
      throw new InvalidRequestException("error.travel_record.completed_only");
    }
  }

  private void validateNotDuplicated(UUID travelId, UUID authorId) {
    if (travelRecordRepository.existsByOriginalTravel_IdAndAuthor_Id(travelId, authorId)) {
      throw new DuplicateResourceException("error.travel_record.duplicate");
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

  private String resolveCloneTitle(
      TravelRecord travelRecord, TravelRecordCloneToTravelReqDto request) {
    if (request.title() != null && !request.title().isBlank()) {
      return request.title();
    }

    if (travelRecord.getTitle() != null && !travelRecord.getTitle().isBlank()) {
      return trimTravelTitle(travelRecord.getTitle());
    }

    return DEFAULT_TITLE;
  }

  private String trimTravelTitle(String title) {
    return title.length() <= MAX_TRAVEL_TITLE_LENGTH
        ? title
        : title.substring(0, MAX_TRAVEL_TITLE_LENGTH);
  }

  private int resolveFeedSize(Integer size) {
    if (size == null) {
      return DEFAULT_FEED_SIZE;
    }

    if (size < 1 || size > MAX_FEED_SIZE) {
      throw new InvalidRequestException("error.travel_record.feed.size_range");
    }

    return size;
  }

  /** 정렬 기준과 커서 유무에 따라 쿼리를 고르던 자리다. 조건이 하나로 합쳐져 분기가 사라졌다. */
  private List<TravelRecord> findFeedRecords(
      FeedCursor feedCursor,
      FeedSearchCondition searchCondition,
      TravelRecordFeedSort sort,
      int limit) {
    TravelRecordFeedSpecifications.Sorting sorting = toSorting(sort);
    return travelRecordRepository.findBy(
        TravelRecordFeedSpecifications.publishedFeed(
            toFeedSearch(searchCondition), sorting, toFeedCursor(feedCursor)),
        query -> query.sortBy(TravelRecordFeedSpecifications.order(sorting)).limit(limit).all());
  }

  private TravelRecordFeedSpecifications.Sorting toSorting(TravelRecordFeedSort sort) {
    return switch (sort) {
      case LATEST -> TravelRecordFeedSpecifications.Sorting.LATEST;
      case MOST_LIKED -> TravelRecordFeedSpecifications.Sorting.MOST_LIKED;
      case MOST_VIEWED -> TravelRecordFeedSpecifications.Sorting.MOST_VIEWED;
    };
  }

  private TravelRecordFeedSpecifications.FeedSearch toFeedSearch(FeedSearchCondition condition) {
    return new TravelRecordFeedSpecifications.FeedSearch(
        condition.hasKeyword() ? condition.keywordPattern() : null,
        condition.hasPlace() ? condition.placeId() : null,
        condition.hasRegion() ? condition.regionPattern() : null,
        condition.hasCity() ? condition.cityPattern() : null,
        condition.hasTravelStartDate() ? condition.travelStartDate() : null,
        condition.hasTravelEndDate() ? condition.travelEndDate() : null);
  }

  private TravelRecordFeedSpecifications.FeedCursor toFeedCursor(FeedCursor feedCursor) {
    return feedCursor == null
        ? null
        : new TravelRecordFeedSpecifications.FeedCursor(
            feedCursor.sortCount(), feedCursor.publishedAt(), feedCursor.createdAt());
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
      throw new InvalidRequestException("error.travel.end_date_before_start");
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
      throw new InvalidRequestException("error.travel_record.feed.cursor_sort_mismatch");
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
      String rawCursor = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] values = rawCursor.split("\\|");
      if (values.length == 2) {
        return new FeedCursor(
            TravelRecordFeedSort.LATEST,
            0L,
            LocalDateTime.parse(values[0]),
            LocalDateTime.parse(values[1]));
      }

      if (values.length != 4) {
        throw new InvalidRequestException("error.travel_record.feed.cursor_invalid");
      }

      return new FeedCursor(
          TravelRecordFeedSort.valueOf(values[0]),
          Long.parseLong(values[1]),
          LocalDateTime.parse(values[2]),
          LocalDateTime.parse(values[3]));
    } catch (IllegalArgumentException exception) {
      throw new InvalidRequestException("error.travel_record.feed.cursor_invalid");
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
