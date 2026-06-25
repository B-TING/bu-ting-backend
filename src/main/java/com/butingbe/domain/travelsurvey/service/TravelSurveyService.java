package com.butingbe.domain.travelsurvey.service;

import com.butingbe.domain.travelsurvey.dto.request.TravelSurveyProfileReqDto;
import com.butingbe.domain.travelsurvey.dto.response.TravelSurveyProfileResDto;
import java.util.UUID;

public interface TravelSurveyService {

  TravelSurveyProfileResDto upsertProfile(UUID userId, TravelSurveyProfileReqDto request);
}
