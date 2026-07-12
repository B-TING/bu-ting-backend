package com.butingbe.domain.travelrecord.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelrecord.dto.request.PlaceReviewCreateReqDto;
import com.butingbe.domain.travelrecord.dto.request.PlaceReviewUpdateReqDto;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordCreateReqDto;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordUpdateReqDto;
import com.butingbe.domain.travelrecord.dto.response.PlaceReviewResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordResDto;
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
