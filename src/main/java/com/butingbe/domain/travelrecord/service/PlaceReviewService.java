package com.butingbe.domain.travelrecord.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.file.service.FileStorageService;
import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travel.entity.PlanPlace;
import com.butingbe.domain.travel.repository.PlanPlaceRepository;
import com.butingbe.domain.travelrecord.dto.request.PlaceReviewCreateReqDto;
import com.butingbe.domain.travelrecord.dto.request.PlaceReviewUpdateReqDto;
import com.butingbe.domain.travelrecord.dto.response.PlaceReviewResDto;
import com.butingbe.domain.travelrecord.dto.response.PlaceReviewSummaryResDto;
import com.butingbe.domain.travelrecord.entity.PlaceReview;
import com.butingbe.domain.travelrecord.entity.PlaceReviewImage;
import com.butingbe.domain.travelrecord.entity.TravelRecord;
import com.butingbe.domain.travelrecord.entity.TravelRecordPlace;
import com.butingbe.domain.travelrecord.entity.TravelRecordStatus;
import com.butingbe.domain.travelrecord.repository.PlaceReviewImageRepository;
import com.butingbe.domain.travelrecord.repository.PlaceReviewRepository;
import com.butingbe.domain.user.entity.User;
import com.butingbe.global.error.exception.DuplicateResourceException;
import com.butingbe.global.error.exception.InvalidRequestException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 리뷰. 여행기 일정의 각 장소에 남기는 평점과 후기다.
 *
 * <p>{@code TravelRecordServiceImpl} 이 1,769 줄이라 유스케이스별로 쪼개는 중이고 그 첫 조각이다. 리뷰는 전용 테이블 두 개만 쓰고 주입
 * 의존성도 셋뿐이라 경계가 가장 뚜렷했다.
 *
 * <p>완전히 독립적이지는 않다. 여행기를 발행할 때 리뷰를 스냅숏으로 복사하고, 여행기 상세 응답에도 리뷰가 실린다. 그 두 지점은 {@link
 * #copyPlaceReviewSnapshot} 과 {@link #toPlaceReviewResponse} 로 열어 둔다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceReviewService {

  private static final int MAX_PLACE_REVIEW_TAG_COUNT = 10;
  private static final int MAX_PLACE_REVIEW_TAG_LENGTH = 30;
  private static final int MAX_PLACE_REVIEW_MEDIA_COUNT = 20;
  private static final int MAX_PLACE_REVIEW_MEDIA_FILE_KEY_LENGTH = 500;

  private final PlaceReviewRepository placeReviewRepository;
  private final PlaceReviewImageRepository placeReviewImageRepository;
  private final FileStorageService fileStorageService;
  private final PlanPlaceRepository planPlaceRepository;
  private final TravelRecordSupport support;

  public PlaceReviewSummaryResDto getPlaceReviewSummary(String placeId) {
    support.validatePlaceId(placeId);

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
                    toPlaceReviewSummaryItem(review, findPlaceReviewMediaUrls(review.getId())))
            .toList());
  }

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
                    toPlaceReviewSummaryItem(review, findPlaceReviewMediaUrls(review.getId())))
            .toList());
  }

  @Transactional
  public PlaceReviewResDto createPlaceReview(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      UUID planPlaceId,
      PlaceReviewCreateReqDto request) {
    User author = support.findAuthenticatedUser(authenticatedUser);
    support.validateTravelMember(travelId, author.getId());
    validatePlaceReviewCreateRequest(request);
    List<String> tags = normalizePlaceReviewTags(request.tags());
    List<String> mediaFileKeys = normalizePlaceReviewMediaFileKeys(request.mediaFileKeys());

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

    savePlaceReviewMedia(placeReview, mediaFileKeys);

    return toPlaceReviewResponse(placeReview);
  }

  public PlaceReviewResDto getPlaceReview(
      AuthenticatedUser authenticatedUser, UUID travelId, UUID planPlaceId) {
    User author = support.findAuthenticatedUser(authenticatedUser);
    support.validateTravelMember(travelId, author.getId());
    findPlanPlaceInTravel(planPlaceId, travelId);

    PlaceReview placeReview = findPlaceReviewByPlanPlaceId(planPlaceId, author.getId());

    return toPlaceReviewResponse(placeReview);
  }

  @Transactional
  public PlaceReviewResDto updatePlaceReview(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      UUID planPlaceId,
      PlaceReviewUpdateReqDto request) {
    User author = support.findAuthenticatedUser(authenticatedUser);
    support.validateTravelMember(travelId, author.getId());
    findPlanPlaceInTravel(planPlaceId, travelId);
    validatePlaceReviewUpdateRequest(request);

    PlaceReview placeReview = findPlaceReviewByPlanPlaceId(planPlaceId, author.getId());
    if (request == null) {
      return PlaceReviewResDto.from(placeReview);
    }

    List<String> tags = request.tags() == null ? null : normalizePlaceReviewTags(request.tags());
    placeReview.update(request.rating(), request.stayMinutes(), request.content(), tags);
    if (request.mediaFileKeys() != null) {
      savePlaceReviewMedia(placeReview, normalizePlaceReviewMediaFileKeys(request.mediaFileKeys()));
    }
    return toPlaceReviewResponse(placeReview);
  }

  @Transactional
  public void deletePlaceReview(
      AuthenticatedUser authenticatedUser, UUID travelId, UUID planPlaceId) {
    User author = support.findAuthenticatedUser(authenticatedUser);
    support.validateTravelMember(travelId, author.getId());
    findPlanPlaceInTravel(planPlaceId, travelId);

    PlaceReview placeReview = findPlaceReviewByPlanPlaceId(planPlaceId, author.getId());
    placeReviewImageRepository.deleteByPlaceReview_Id(placeReview.getId());
    placeReviewRepository.delete(placeReview);
  }

  public void copyPlaceReviewSnapshot(PlanPlace sourcePlace, TravelRecordPlace recordPlace) {
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
              PlaceReview savedReview = placeReviewRepository.save(snapshotReview);
              copyPlaceReviewMedia(sourceReview, savedReview);
            });
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
        .map(this::toMediaUrl)
        .toList();
  }

  private String toMediaUrl(PlaceReviewImage image) {
    return image.getFileKey() == null
        ? image.getExternalUrl()
        : fileStorageService.getPresignedUrl(image.getFileKey());
  }

  private void savePlaceReviewMedia(PlaceReview placeReview, List<String> mediaFileKeys) {
    placeReviewImageRepository.deleteByPlaceReview_Id(placeReview.getId());
    for (int index = 0; index < mediaFileKeys.size(); index++) {
      placeReviewImageRepository.save(
          PlaceReviewImage.builder()
              .placeReview(placeReview)
              .fileKey(mediaFileKeys.get(index))
              .externalUrl(null)
              .sequence(index + 1)
              .build());
    }
  }

  private void copyPlaceReviewMedia(PlaceReview sourceReview, PlaceReview targetReview) {
    placeReviewImageRepository.findByPlaceReview_IdOrderBySequenceAsc(sourceReview.getId()).stream()
        .map(
            image ->
                PlaceReviewImage.builder()
                    .placeReview(targetReview)
                    .fileKey(image.getFileKey())
                    .externalUrl(image.getExternalUrl())
                    .sequence(image.getSequence())
                    .build())
        .forEach(placeReviewImageRepository::save);
  }

  private PlanPlace findPlanPlaceInTravel(UUID planPlaceId, UUID travelId) {
    PlanPlace planPlace =
        planPlaceRepository
            .findById(planPlaceId)
            .orElseThrow(() -> new ResourceNotFoundException("error.travel.plan_place.not_found"));

    if (!planPlace.getPlan().getTravel().getId().equals(travelId)) {
      throw new ResourceNotFoundException("error.travel.plan_place.not_found");
    }

    return planPlace;
  }

  private PlaceReview findPlaceReviewByPlanPlaceId(UUID planPlaceId, UUID authorId) {
    return placeReviewRepository
        .findByPlanPlace_IdAndAuthor_Id(planPlaceId, authorId)
        .orElseThrow(
            () -> new ResourceNotFoundException("error.travel_record.place_review.not_found"));
  }

  private void validatePlaceReviewCreateRequest(PlaceReviewCreateReqDto request) {
    if (request == null || request.rating() == null) {
      throw new InvalidRequestException("error.travel_record.place_review.rating_required");
    }

    if (request.rating() < 1 || request.rating() > 5) {
      throw new InvalidRequestException("error.travel_record.place_review.rating_range");
    }

    validatePlaceReviewStayMinutes(request.stayMinutes());
  }

  private void validatePlaceReviewUpdateRequest(PlaceReviewUpdateReqDto request) {
    if (request == null) {
      return;
    }

    if (request.rating() != null && (request.rating() < 1 || request.rating() > 5)) {
      throw new InvalidRequestException("error.travel_record.place_review.rating_range");
    }

    validatePlaceReviewStayMinutes(request.stayMinutes());
  }

  private void validatePlaceReviewStayMinutes(Integer stayMinutes) {
    if (stayMinutes == null) {
      return;
    }

    if (stayMinutes < 0) {
      throw new InvalidRequestException("error.travel_record.stay_minutes_invalid");
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
      throw new InvalidRequestException(
          "Place review tags must be " + MAX_PLACE_REVIEW_TAG_COUNT + " or fewer.");
    }

    boolean hasTooLongTag =
        normalizedTags.stream().anyMatch(tag -> tag.length() > MAX_PLACE_REVIEW_TAG_LENGTH);
    if (hasTooLongTag) {
      throw new InvalidRequestException(
          "Place review tag must be " + MAX_PLACE_REVIEW_TAG_LENGTH + " characters or less.");
    }

    return normalizedTags;
  }

  private List<String> normalizePlaceReviewMediaFileKeys(List<String> mediaFileKeys) {
    if (mediaFileKeys == null || mediaFileKeys.isEmpty()) {
      return List.of();
    }

    List<String> normalizedMediaFileKeys =
        mediaFileKeys.stream()
            .filter(fileKey -> fileKey != null && !fileKey.isBlank())
            .map(String::trim)
            .distinct()
            .toList();

    if (normalizedMediaFileKeys.size() > MAX_PLACE_REVIEW_MEDIA_COUNT) {
      throw new InvalidRequestException(
          "Place review media file keys must be " + MAX_PLACE_REVIEW_MEDIA_COUNT + " or fewer.");
    }

    boolean hasTooLongFileKey =
        normalizedMediaFileKeys.stream()
            .anyMatch(fileKey -> fileKey.length() > MAX_PLACE_REVIEW_MEDIA_FILE_KEY_LENGTH);
    if (hasTooLongFileKey) {
      throw new InvalidRequestException(
          "Place review media file key must be "
              + MAX_PLACE_REVIEW_MEDIA_FILE_KEY_LENGTH
              + " characters or less.");
    }

    return normalizedMediaFileKeys;
  }

  private void validatePlaceReviewSummaryRequest(PlaceProvider provider, String providerPlaceId) {
    if (provider == null) {
      throw new InvalidRequestException("error.place.provider_required");
    }

    support.validatePlaceId(providerPlaceId);
  }

  private void validatePlaceReviewNotDuplicated(UUID planPlaceId, UUID authorId) {
    if (placeReviewRepository.findByPlanPlace_IdAndAuthor_Id(planPlaceId, authorId).isPresent()) {
      throw new DuplicateResourceException("error.travel_record.place_review.duplicate");
    }
  }

  private double calculateAverageRating(List<PlaceReview> reviews) {
    if (reviews.isEmpty()) {
      return 0.0;
    }

    double average = reviews.stream().mapToInt(PlaceReview::getRating).average().orElse(0.0);

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
}
