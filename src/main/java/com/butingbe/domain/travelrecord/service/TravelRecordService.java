package com.butingbe.domain.travelrecord.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.entity.PlaceProvider;
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
import java.util.List;
import java.util.UUID;

public interface TravelRecordService {

  TravelRecordResDto createDraft(
      AuthenticatedUser authenticatedUser, UUID travelId, TravelRecordCreateReqDto request);

  TravelRecordResDto getDraft(
      AuthenticatedUser authenticatedUser, UUID travelId, UUID travelRecordId);

  TravelRecordResDto updateDraft(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      UUID travelRecordId,
      TravelRecordUpdateReqDto request);

  TravelRecordResDto publish(
      AuthenticatedUser authenticatedUser, UUID travelId, UUID travelRecordId);

  TravelRecordResDto getPublished(UUID travelRecordId);

  TravelRecordFeedPageResDto getLatestFeed(String cursor, Integer size);

  List<TravelRecordManageResDto> getMyRecords(AuthenticatedUser authenticatedUser);

  TravelRecordResDto getMyRecord(AuthenticatedUser authenticatedUser, UUID travelRecordId);

  TravelRecordResDto updateMyRecord(
      AuthenticatedUser authenticatedUser, UUID travelRecordId, TravelRecordUpdateReqDto request);

  TravelRecordResDto hideMyRecord(AuthenticatedUser authenticatedUser, UUID travelRecordId);

  TravelRecordResDto republishMyRecord(AuthenticatedUser authenticatedUser, UUID travelRecordId);

  TravelRecordBookmarkResDto bookmarkTravelRecord(
      AuthenticatedUser authenticatedUser, UUID travelRecordId);

  void unbookmarkTravelRecord(AuthenticatedUser authenticatedUser, UUID travelRecordId);

  List<TravelRecordBookmarkResDto> getMyBookmarkedRecords(AuthenticatedUser authenticatedUser);

  TravelRecordLikeResDto likeTravelRecord(AuthenticatedUser authenticatedUser, UUID travelRecordId);

  void unlikeTravelRecord(AuthenticatedUser authenticatedUser, UUID travelRecordId);

  List<TravelRecordFeedResDto> getTravelRecordsByPlace(
      PlaceProvider provider, String providerPlaceId);

  PlaceReviewSummaryResDto getPlaceReviewSummary(PlaceProvider provider, String providerPlaceId);

  PlaceReviewResDto createPlaceReview(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      UUID travelRecordId,
      UUID travelRecordPlaceId,
      PlaceReviewCreateReqDto request);

  PlaceReviewResDto getPlaceReview(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      UUID travelRecordId,
      UUID travelRecordPlaceId);

  PlaceReviewResDto updatePlaceReview(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      UUID travelRecordId,
      UUID travelRecordPlaceId,
      PlaceReviewUpdateReqDto request);

  void deletePlaceReview(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      UUID travelRecordId,
      UUID travelRecordPlaceId);
}
