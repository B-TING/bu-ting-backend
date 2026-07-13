package com.butingbe.domain.travelrecord.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
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
import com.butingbe.domain.travelrecord.dto.request.TravelRecordCreateReqDto;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordUpdateReqDto;
import com.butingbe.domain.travelrecord.dto.response.PlaceReviewResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordFeedResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordResDto.TravelRecordDayResDto;
import com.butingbe.domain.travelrecord.entity.PlaceReview;
import com.butingbe.domain.travelrecord.entity.TravelRecord;
import com.butingbe.domain.travelrecord.entity.TravelRecordDay;
import com.butingbe.domain.travelrecord.entity.TravelRecordPlace;
import com.butingbe.domain.travelrecord.entity.TravelRecordRoute;
import com.butingbe.domain.travelrecord.entity.TravelRecordStatus;
import com.butingbe.domain.travelrecord.repository.PlaceReviewRepository;
import com.butingbe.domain.travelrecord.repository.TravelRecordDayRepository;
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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelRecordServiceImpl implements TravelRecordService {

  private static final String DEFAULT_TITLE = "여행 기록";

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

  @Override
  @Transactional
  public TravelRecordResDto createDraft(
      AuthenticatedUser authenticatedUser, UUID travelId, TravelRecordCreateReqDto request) {
    User author = findAuthenticatedUser(authenticatedUser);
    Travel travel = findTravel(travelId);
    validateTravelMember(travelId, author.getId());
    validateCompletedTravel(travel);
    validateNotDuplicated(travelId, author.getId());

    TravelRecord travelRecord =
        travelRecordRepository.save(
            TravelRecord.builder()
                .originalTravel(travel)
                .author(author)
                .title(resolveTitle(travel, request))
                .content(request == null ? null : request.content())
                .coverImageUrl(request == null ? null : request.coverImageUrl())
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

    travelRecord.updateContent(request.title(), request.content(), request.coverImageUrl());

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
  public TravelRecordResDto getPublished(UUID travelRecordId) {
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validatePublished(travelRecord);

    return toResponse(travelRecord);
  }

  @Override
  public List<TravelRecordFeedResDto> getLatestFeed() {
    return travelRecordRepository
        .findByStatusOrderByPublishedAtDescCreatedAtDesc(TravelRecordStatus.PUBLISHED)
        .stream()
        .map(TravelRecordFeedResDto::from)
        .toList();
  }

  @Override
  @Transactional
  public PlaceReviewResDto createPlaceReview(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      UUID travelRecordId,
      UUID travelRecordPlaceId,
      PlaceReviewCreateReqDto request) {
    User author = findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validateDraftBelongsToTravel(travelRecord, travelId);
    validateAuthor(travelRecord, author.getId());
    validateDraft(travelRecord);
    validatePlaceReviewCreateRequest(request);

    TravelRecordPlace travelRecordPlace =
        findTravelRecordPlaceInRecord(travelRecordPlaceId, travelRecordId);
    validatePlaceReviewNotDuplicated(travelRecordPlaceId);

    PlaceReview placeReview =
        placeReviewRepository.save(
            PlaceReview.builder()
                .travelRecordPlace(travelRecordPlace)
                .rating(request.rating())
                .content(request.content())
                .build());

    return PlaceReviewResDto.from(placeReview);
  }

  @Override
  public PlaceReviewResDto getPlaceReview(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      UUID travelRecordId,
      UUID travelRecordPlaceId) {
    User author = findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validateDraftBelongsToTravel(travelRecord, travelId);
    validateAuthor(travelRecord, author.getId());
    validateDraft(travelRecord);
    findTravelRecordPlaceInRecord(travelRecordPlaceId, travelRecordId);

    PlaceReview placeReview =
        placeReviewRepository
            .findByTravelRecordPlace_Id(travelRecordPlaceId)
            .orElseThrow(() -> new ResourceNotFoundException("Place review not found."));

    return PlaceReviewResDto.from(placeReview);
  }

  @Override
  @Transactional
  public PlaceReviewResDto updatePlaceReview(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      UUID travelRecordId,
      UUID travelRecordPlaceId,
      PlaceReviewUpdateReqDto request) {
    User author = findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validateDraftBelongsToTravel(travelRecord, travelId);
    validateAuthor(travelRecord, author.getId());
    validateDraft(travelRecord);
    findTravelRecordPlaceInRecord(travelRecordPlaceId, travelRecordId);
    validatePlaceReviewUpdateRequest(request);

    PlaceReview placeReview = findPlaceReviewByTravelRecordPlaceId(travelRecordPlaceId);
    if (request == null) {
      return PlaceReviewResDto.from(placeReview);
    }

    placeReview.update(request.rating(), request.content());
    return PlaceReviewResDto.from(placeReview);
  }

  @Override
  @Transactional
  public void deletePlaceReview(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      UUID travelRecordId,
      UUID travelRecordPlaceId) {
    User author = findAuthenticatedUser(authenticatedUser);
    TravelRecord travelRecord = findTravelRecord(travelRecordId);
    validateDraftBelongsToTravel(travelRecord, travelId);
    validateAuthor(travelRecord, author.getId());
    validateDraft(travelRecord);
    findTravelRecordPlaceInRecord(travelRecordPlaceId, travelRecordId);

    PlaceReview placeReview = findPlaceReviewByTravelRecordPlaceId(travelRecordPlaceId);
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

  private TravelRecordResDto toResponse(TravelRecord travelRecord) {
    List<TravelRecordDayResDto> days =
        travelRecordDayRepository.findByTravelRecord_IdOrderByDayNumberAsc(travelRecord.getId())
            .stream()
            .map(this::toDayResponse)
            .toList();

    return TravelRecordResDto.of(travelRecord, days);
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

  private PlaceReview findPlaceReviewByTravelRecordPlaceId(UUID travelRecordPlaceId) {
    return placeReviewRepository
        .findByTravelRecordPlace_Id(travelRecordPlaceId)
        .orElseThrow(() -> new ResourceNotFoundException("Place review not found."));
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
  }

  private void validatePublished(TravelRecord travelRecord) {
    if (travelRecord.getStatus() != TravelRecordStatus.PUBLISHED) {
      throw new ResourceNotFoundException("Travel record not found.");
    }
  }

  private void validateUpdateRequest(TravelRecordUpdateReqDto request) {
    if (request == null) {
      return;
    }

    if (request.title() != null && request.title().isBlank()) {
      throw new IllegalArgumentException("Travel record title cannot be blank.");
    }
  }

  private void validatePlaceReviewCreateRequest(PlaceReviewCreateReqDto request) {
    if (request == null || request.rating() == null) {
      throw new IllegalArgumentException("Place review rating is required.");
    }

    if (request.rating() < 1 || request.rating() > 5) {
      throw new IllegalArgumentException("Place review rating must be between 1 and 5.");
    }
  }

  private void validatePlaceReviewUpdateRequest(PlaceReviewUpdateReqDto request) {
    if (request == null || request.rating() == null) {
      return;
    }

    if (request.rating() < 1 || request.rating() > 5) {
      throw new IllegalArgumentException("Place review rating must be between 1 and 5.");
    }
  }

  private void validatePlaceReviewNotDuplicated(UUID travelRecordPlaceId) {
    if (placeReviewRepository.findByTravelRecordPlace_Id(travelRecordPlaceId).isPresent()) {
      throw new DuplicateResourceException("Place review already exists.");
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
}
