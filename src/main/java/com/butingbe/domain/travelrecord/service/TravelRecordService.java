package com.butingbe.domain.travelrecord.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travelrecord.dto.request.PlaceReviewCreateReqDto;
import com.butingbe.domain.travelrecord.dto.request.PlaceReviewUpdateReqDto;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordCommentCreateReqDto;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordCommentUpdateReqDto;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordFeedSort;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordCreateReqDto;
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
import java.time.LocalDate;
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

  TravelRecordFeedPageResDto getLatestFeed(
      String cursor,
      Integer size,
      String keyword,
      PlaceProvider provider,
      String providerPlaceId,
      LocalDate travelStartDate,
      LocalDate travelEndDate,
      TravelRecordFeedSort sort);

  TravelRecordFeedPageResDto getLatestFeed(
      String cursor,
      Integer size,
      String keyword,
      PlaceProvider provider,
      String providerPlaceId,
      LocalDate travelStartDate,
      LocalDate travelEndDate,
      String region,
      String city,
      TravelRecordFeedSort sort);

  TravelRecordFeedPageResDto getLatestFeed(
      AuthenticatedUser authenticatedUser,
      String cursor,
      Integer size,
      String keyword,
      String placeId,
      LocalDate travelStartDate,
      LocalDate travelEndDate,
      String region,
      String city,
      TravelRecordFeedSort sort);

  TravelRecordFeedPageResDto getLatestFeed(
      String cursor,
      Integer size,
      String keyword,
      PlaceProvider provider,
      String providerPlaceId,
      LocalDate travelStartDate,
      LocalDate travelEndDate);

  TravelRecordFeedPageResDto getLatestFeed(
      String cursor,
      Integer size,
      String keyword,
      PlaceProvider provider,
      String providerPlaceId,
      LocalDate travelStartDate,
      LocalDate travelEndDate,
      String region,
      String city);

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

  TravelRecordCommentResDto createComment(
      AuthenticatedUser authenticatedUser,
      UUID travelRecordId,
      TravelRecordCommentCreateReqDto request);

  List<TravelRecordCommentResDto> getComments(UUID travelRecordId);

  TravelRecordCommentResDto updateComment(
      AuthenticatedUser authenticatedUser,
      UUID travelRecordId,
      UUID commentId,
      TravelRecordCommentUpdateReqDto request);

  void deleteComment(AuthenticatedUser authenticatedUser, UUID travelRecordId, UUID commentId);

  List<TravelRecordFeedResDto> getTravelRecordsByPlace(
      PlaceProvider provider, String providerPlaceId);

  TravelRecordFeedPageResDto getTravelRecordsByPlace(
      PlaceProvider provider, String providerPlaceId, String cursor, Integer size);

  TravelRecordFeedPageResDto getTravelRecordsByPlace(
      AuthenticatedUser authenticatedUser,
      String placeId,
      String cursor,
      Integer size);

  PlaceReviewSummaryResDto getPlaceReviewSummary(String placeId);

  PlaceReviewSummaryResDto getPlaceReviewSummary(PlaceProvider provider, String providerPlaceId);

  PlaceReviewResDto createPlaceReview(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      UUID planPlaceId,
      PlaceReviewCreateReqDto request);

  PlaceReviewResDto getPlaceReview(
      AuthenticatedUser authenticatedUser, UUID travelId, UUID planPlaceId);

  PlaceReviewResDto updatePlaceReview(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      UUID planPlaceId,
      PlaceReviewUpdateReqDto request);

  void deletePlaceReview(AuthenticatedUser authenticatedUser, UUID travelId, UUID planPlaceId);
}
