package com.butingbe.domain.travelrecord.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordCreateReqDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordResDto;
import java.util.UUID;

public interface TravelRecordService {

  TravelRecordResDto createDraft(
      AuthenticatedUser authenticatedUser, UUID travelId, TravelRecordCreateReqDto request);
}
