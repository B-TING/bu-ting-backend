package com.butingbe.domain.travel.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.dto.request.PlanCreateReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceCreateReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceSequenceUpdateReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceUpdatePlaceReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceUpdateReqDto;
import com.butingbe.domain.travel.dto.request.TravelCreateReqDto;
import com.butingbe.domain.travel.dto.response.PlanPlaceResDto;
import com.butingbe.domain.travel.dto.response.PlanResDto;
import com.butingbe.domain.travel.dto.response.TravelPlansResDto;
import com.butingbe.domain.travel.dto.response.TravelResDto;
import java.util.List;
import java.util.UUID;

public interface TravelService {

  TravelResDto createTravel(AuthenticatedUser authenticatedUser, TravelCreateReqDto request);

  TravelPlansResDto getTravelPlans(AuthenticatedUser authenticatedUser, UUID travelId);

  PlanResDto createPlan(
      AuthenticatedUser authenticatedUser, UUID travelId, PlanCreateReqDto request);

  void deletePlan(AuthenticatedUser authenticatedUser, UUID travelId, UUID planId);

  PlanPlaceResDto createPlanPlace(
      AuthenticatedUser authenticatedUser, UUID planId, PlanPlaceCreateReqDto request);

  List<PlanPlaceResDto> getPlanPlaces(AuthenticatedUser authenticatedUser, UUID planId);

  PlanPlaceResDto updatePlanPlace(
      AuthenticatedUser authenticatedUser, UUID planPlaceId, PlanPlaceUpdateReqDto request);

  PlanPlaceResDto updatePlanPlacePlace(
      AuthenticatedUser authenticatedUser, UUID planPlaceId, PlanPlaceUpdatePlaceReqDto request);

  List<PlanPlaceResDto> updatePlanPlaceSequence(
      AuthenticatedUser authenticatedUser, UUID planId, PlanPlaceSequenceUpdateReqDto request);

  void deletePlanPlace(AuthenticatedUser authenticatedUser, UUID planPlaceId);
}
