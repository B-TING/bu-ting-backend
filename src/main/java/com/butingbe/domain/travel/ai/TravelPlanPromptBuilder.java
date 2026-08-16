package com.butingbe.domain.travel.ai;

import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto;
import com.butingbe.domain.travel.entity.Travel;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

@Component
public class TravelPlanPromptBuilder {
  public String build(Travel travel) {
    return build(travel, null);
  }

  public String build(Travel travel, AiTravelPlanGenerateReqDto request) {
    long days = ChronoUnit.DAYS.between(travel.getStartDate(), travel.getEndDate()) + 1;
    return """
        여행 플랜을 JSON으로만 생성하세요. Markdown과 설명문은 금지합니다.
        여행 지역: %s
        여행 기간: %s ~ %s (%d일)
        숙소 주변 지역: %s
        여행 스타일: %s
        여행 속도: %s
        동행 인원: %s
        선호 음식: %s
        위저드 음식 태그: %s
        여행 목적: %s
        위저드 일정 페이스: %s
        예약 숙소: %s
        숙소 권역: %s
        선택 가능한 실제 장소 목록: %s
        JSON schema: {"days":[{"date":"YYYY-MM-DD","places":[{"order":1,"name":"장소명","description":"설명","recommendationReason":"추천 이유"}]}]}
        여행 지역을 일정 계획의 최우선 기준으로 삼고, 여행 지역 밖의 장소는 추천하지 마세요.
        숙소 주변 지역을 동선의 출발·복귀 기준으로 고려하세요.
        선택된 실제 장소는 가능한 한 일정에 반드시 포함하고, 날짜와 장소를 균형 있게 배치하세요.
        장소 목록 밖의 장소를 임의로 생성하지 말고, 날짜 범위를 벗어나지 마세요.
        모든 날짜를 빠짐없이 생성하고 각 날짜의 order는 1부터 시작하세요.
        """
        .formatted(
            travel.getDestination(),
            travel.getStartDate(),
            travel.getEndDate(),
            days,
            value(travel.getAccommodationArea()),
            value(travel.getTravelStyle()),
            value(travel.getPace()),
            value(travel.getCompanionCount()),
            value(travel.getPreferredFoods()),
            request == null ? "없음" : value(request.foodIds()),
            request == null ? "없음" : value(request.purposes()),
            request == null ? "없음" : value(request.schedulePace()),
            request == null ? "없음" : value(request.bookedAccommodation()),
            request == null ? "없음" : value(request.accommodationAreaIds()),
            request == null
                ? "없음"
                : request.selectedPlaces().stream()
                    .map(
                        place ->
                            place.providerPlaceId()
                                + " | "
                                + place.placeName()
                                + " | "
                                + value(place.type()))
                    .toList());
  }

  private String value(Object value) {
    return value == null ? "없음" : value.toString();
  }
}
