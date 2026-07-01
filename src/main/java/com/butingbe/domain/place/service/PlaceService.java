package com.butingbe.domain.place.service;

import com.butingbe.domain.place.dto.request.PlaceLocationSearchReqDto;
import com.butingbe.domain.place.dto.request.PlaceSearchReqDto;
import com.butingbe.domain.place.dto.response.PlaceDetailResDto;
import com.butingbe.domain.place.dto.response.PlaceSearchResDto;

public interface PlaceService {

  PlaceSearchResDto searchPlaces(PlaceSearchReqDto request);

  PlaceSearchResDto searchPlacesByLocation(PlaceLocationSearchReqDto request);


  PlaceDetailResDto getPlaceDetail(String contentId, String contentTypeId, String googleSearchText);
}
