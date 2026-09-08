package com.butingbe.domain.place.service;

import com.butingbe.domain.place.dto.request.FestivalSearchReqDto;
import com.butingbe.domain.place.dto.request.PlaceKeywordSearchReqDto;
import com.butingbe.domain.place.dto.request.PlaceLocationSearchReqDto;
import com.butingbe.domain.place.dto.request.PlaceSearchReqDto;
import com.butingbe.domain.place.dto.response.FestivalSearchResDto;
import com.butingbe.domain.place.dto.response.PlaceDetailResDto;
import com.butingbe.domain.place.dto.response.PlaceSearchResDto;
import com.butingbe.domain.place.dto.response.PlaceSummaryResDto;

public interface PlaceService {

  PlaceSearchResDto searchPlaces(PlaceSearchReqDto request);

  PlaceSearchResDto searchPlacesByKeyword(PlaceKeywordSearchReqDto request);

  PlaceSearchResDto searchPlacesByLocation(PlaceLocationSearchReqDto request);

  FestivalSearchResDto searchFestivals(FestivalSearchReqDto request);

  PlaceDetailResDto getPlaceDetail(String contentId, String contentTypeId, String googleSearchText);

  /** 관광지 마스터 데이터를 읽기 전용으로 조회한다(제목 + 원본 좌표). 없으면 null. */
  PlaceSummaryResDto getPlaceSummary(String contentId);
}
