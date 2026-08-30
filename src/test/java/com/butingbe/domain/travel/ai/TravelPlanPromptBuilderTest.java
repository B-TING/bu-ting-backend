package com.butingbe.domain.travel.ai;

import static com.butingbe.domain.travel.ai.TravelPlanFixtures.IDS;
import static com.butingbe.domain.travel.ai.TravelPlanFixtures.request;
import static com.butingbe.domain.travel.ai.TravelPlanFixtures.travel;
import static org.assertj.core.api.Assertions.assertThat;

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
}
