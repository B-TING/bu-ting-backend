package com.butingbe.domain.travel.ai;

import static com.butingbe.domain.travel.ai.TravelPlanFixtures.IDS;
import static com.butingbe.domain.travel.ai.TravelPlanFixtures.request;
import static com.butingbe.domain.travel.ai.TravelPlanFixtures.travel;
import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.travel.entity.CompanionType;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.entity.TravelStatus;
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

  @Test
  @DisplayName("위저드 제약이 설정되면 해당 배치 지시문을 덧붙인다")
  void appendsDirectivesForConfiguredConstraints() {
    String prompt =
        new TravelPlanPromptBuilder()
            .build(constrainedTravel(CompanionType.FAMILY, true, true, true));

    assertThat(prompt).contains("동행 유형: FAMILY", "짐 많음: 예", "평지 선호: 예", "반려동물 동반: 예");
    assertThat(prompt)
        .contains(
            "가족 동행입니다.",
            "첫날 첫 장소는 숙소 권역에서 가장 가까운 곳으로",
            "경사나 계단이 많은 장소를 연속으로",
            "동반 가능 여부를 미리 확인하라는 안내");
  }

  @Test
  @DisplayName("제약이 없으면 지시문을 넣지 않는다")
  void omitsDirectivesWhenConstraintsAbsent() {
    String prompt = new TravelPlanPromptBuilder().build(travel());

    assertThat(prompt).contains("동행 유형: 없음", "짐 많음: 없음", "평지 선호: 없음", "반려동물 동반: 없음");
    assertThat(prompt).doesNotContain("가족 동행입니다.", "경사나 계단이", "동반 가능 여부를");
  }

  @Test
  @DisplayName("제약 플래그가 false면 값만 적고 지시문은 넣지 않는다")
  void omitsDirectivesWhenConstraintsDisabled() {
    String prompt =
        new TravelPlanPromptBuilder()
            .build(constrainedTravel(CompanionType.SOLO, false, false, false));

    assertThat(prompt).contains("동행 유형: SOLO", "짐 많음: 아니오", "평지 선호: 아니오", "반려동물 동반: 아니오");
    assertThat(prompt).doesNotContain("가족 동행입니다.", "짐이 많습니다.", "평지를 선호합니다.", "반려동물을 동반합니다.");
  }

  @Test
  @DisplayName("부산 여행이면 권역·시간대 배치 기준을 덧붙인다")
  void appendsBusanCourseGuide() {
    String prompt = new TravelPlanPromptBuilder().build(travel());

    assertThat(prompt)
        .contains(
            "부산 권역별 배치 기준:",
            "같은 권역의 장소는 같은 날에 묶으세요.",
            "야경이 핵심인 장소는 그날의 마지막 순서로",
            "일몰이 핵심인 장소는 늦은 오후로",
            "선택 목록에 없으면 추가하지 마세요.");
  }

  @Test
  @DisplayName("부산이 아닌 여행에는 권역 기준을 붙이지 않는다")
  void skipsCourseGuideOutsideBusan() {
    String prompt = new TravelPlanPromptBuilder().build(travelTo("제주"));

    assertThat(prompt).doesNotContain("부산 권역별 배치 기준:");
  }

  @Test
  @DisplayName("여행 지역이 없으면 권역 기준을 붙이지 않는다")
  void skipsCourseGuideWithoutDestination() {
    String prompt = new TravelPlanPromptBuilder().build(travelTo(null));

    assertThat(prompt).doesNotContain("부산 권역별 배치 기준:");
  }

  private Travel constrainedTravel(
      CompanionType companionTypes, boolean heavyBaggage, boolean flatTerrain, boolean pets) {
    return Travel.builder()
        .title("부산 여행")
        .destination("부산")
        .startDate(TravelPlanFixtures.START)
        .endDate(TravelPlanFixtures.START.plusDays(2))
        .status(TravelStatus.PLANNED)
        .companionTypes(companionTypes)
        .hasHeavyBaggage(heavyBaggage)
        .preferFlatTerrain(flatTerrain)
        .hasPets(pets)
        .build();
  }

  private Travel travelTo(String destination) {
    return Travel.builder()
        .title("여행")
        .destination(destination)
        .startDate(TravelPlanFixtures.START)
        .endDate(TravelPlanFixtures.START.plusDays(1))
        .status(TravelStatus.PLANNED)
        .build();
  }
}
