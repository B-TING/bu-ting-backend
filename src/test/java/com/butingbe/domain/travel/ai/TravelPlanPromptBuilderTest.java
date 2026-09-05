package com.butingbe.domain.travel.ai;

import static com.butingbe.domain.travel.ai.TravelPlanFixtures.IDS;
import static com.butingbe.domain.travel.ai.TravelPlanFixtures.request;
import static com.butingbe.domain.travel.ai.TravelPlanFixtures.travel;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TravelPlanPromptBuilderTest {
  @Test
  void suppliesAllContentIdsLocationsPreferencesAndAccommodationContext() {
    String prompt = new TravelPlanPromptBuilder().build(travel(), request());
    assertThat(prompt).contains(IDS.toArray(String[]::new));
    assertThat(prompt)
        .contains(
            "선택한 전체 장소 수: 8",
            "BALANCED",
            "milmyeon",
            "자연·힐링",
            "파라다이스 호텔 부산",
            "haeundae",
            "부산 원본 주소 0",
            "35.1",
            "129.0",
            "정확히 한 번",
            "장소 수의 상한이 아닙니다",
            "추가하지 마세요",
            "contentId");
  }

  @Test
  @DisplayName("위저드 요청이 없으면 관련 항목을 모두 '없음'으로 채운다")
  void buildWithoutWizardRequestFillsPlaceholders() {
    String prompt = new TravelPlanPromptBuilder().build(travel());

    assertThat(prompt).contains("위저드 음식 태그: 없음");
    assertThat(prompt).contains("여행 목적: 없음");
    assertThat(prompt).contains("위저드 일정 페이스: 없음");
    assertThat(prompt).contains("예약 숙소: 없음");
    assertThat(prompt).contains("숙소 권역: 없음");
    assertThat(prompt).contains("선택한 전체 장소 수: 0");
  }
}
