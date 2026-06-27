package com.butingbe.domain.travelsurvey.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelsurvey.dto.request.TravelSurveyProfileReqDto;
import com.butingbe.domain.travelsurvey.dto.response.TravelSurveyProfileResDto;

public interface TravelSurveyService {

  TravelSurveyProfileResDto upsertProfile(
      AuthenticatedUser authenticatedUser, TravelSurveyProfileReqDto request);

  TravelSurveyProfileResDto getProfile(AuthenticatedUser authenticatedUser);
}
