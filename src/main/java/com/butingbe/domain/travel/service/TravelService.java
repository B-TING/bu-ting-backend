package com.butingbe.domain.travel.service;

import com.butingbe.domain.travel.dto.request.TravelCreateReqDto;
import com.butingbe.domain.travel.dto.response.TravelResDto;

public interface TravelService {

  TravelResDto createTravel(TravelCreateReqDto request);
}
