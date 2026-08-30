package com.butingbe.domain.travel.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto.WizardPickedPlaceReqDto;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TravelPlanQualityValidatorTest {
  private final TravelPlanRoutePlanner planner = new TravelPlanRoutePlanner();
  private final TravelPlanQualityValidator validator = new TravelPlanQualityValidator(planner);

  @Test
  void catchesExcessiveBacktrackingAndChangedDayGroups() {
    var input =
        IntStream.range(0, 4)
            .mapToObj(
                i ->
                    new WizardPickedPlaceReqDto(
                        "GOOGLE", "id" + i, "장소" + i, "주소", 35.0, 129.0 + i * 0.1, "TOURIST_SPOT"))
            .toList();
    var catalog = SelectedPlaceCatalog.from(TravelPlanFixtures.request(input));
    var keys = List.copyOf(catalog.keySet());
    var date = TravelPlanFixtures.START;
    var bad =
        new TravelPlanAiResponse(
            List.of(
                new TravelPlanAiResponse.Day(
                    date,
                    IntStream.range(0, 4)
                        .mapToObj(
                            i -> {
                              var key = keys.get(List.of(0, 3, 1, 2).get(i));
                              return new TravelPlanAiResponse.Place(
                                  i + 1,
                                  "GOOGLE",
                                  key.providerPlaceId(),
                                  catalog.get(key).placeName()
                                      + "에서는 활동 "
                                      + i
                                      + "을 중심으로 주변 풍경을 살펴보며 여행 목적에 맞게 방문하세요.");
                            })
                        .toList())));
    assertThat(validator.feedback(bad, Map.of(date, keys), catalog))
        .anyMatch(s -> s.contains("왕복"));
    assertThat(validator.feedback(bad, Map.of(date, keys.subList(0, 2)), catalog))
        .anyMatch(s -> s.contains("날짜별 장소 묶음"));
  }

  @Test
  void rejectsCopiedDescriptionsEvenWhenOnlyPlaceNameChanges() {
    var catalog = SelectedPlaceCatalog.from(TravelPlanFixtures.request());
    var response =
        new TravelPlanAiResponse(
            TravelPlanFixtures.qualityResponse().days().stream()
                .map(
                    day ->
                        new TravelPlanAiResponse.Day(
                            day.date(),
                            day.places().stream()
                                .map(
                                    p ->
                                        new TravelPlanAiResponse.Place(
                                            p.order(),
                                            p.provider(),
                                            p.providerPlaceId(),
                                            catalog
                                                    .get(
                                                        PlaceKey.of(
                                                            p.provider(), p.providerPlaceId()))
                                                    .placeName()
                                                + "에서 주변 풍경을 살펴보며 여유롭게 시간을 보내기 좋습니다."))
                                .toList()))
                .toList());
    assertThat(
            validator.feedback(
                response, planner.plan(TravelPlanFixtures.travel(), catalog), catalog))
        .hasSize(7);
  }

  @Test
  void skipsDistanceChecksForUnknownCoordinatesWithoutDroppingPlaces() {
    var unknown =
        new WizardPickedPlaceReqDto("GOOGLE", "id", "장소", "주소", null, null, "TOURIST_SPOT");
    var catalog = SelectedPlaceCatalog.from(TravelPlanFixtures.request(List.of(unknown)));
    var routes = planner.plan(TravelPlanFixtures.travel(), catalog);
    var response =
        new TravelPlanAiResponse(
            routes.entrySet().stream()
                .map(
                    entry ->
                        new TravelPlanAiResponse.Day(
                            entry.getKey(),
                            entry.getValue().stream()
                                .map(
                                    key ->
                                        new TravelPlanAiResponse.Place(
                                            1,
                                            "GOOGLE",
                                            "id",
                                            "장소에서는 사용자가 선택한 여행 목적을 고려하여 현장의 분위기를 천천히 살펴보세요."))
                                .toList()))
                .toList());
    assertThat(validator.feedback(response, routes, catalog)).isEmpty();
  }

  @Test
  void rejectsMemoAttachedToWrongPlaceId() {
    var catalog = SelectedPlaceCatalog.from(TravelPlanFixtures.request());
    var response =
        new TravelPlanAiResponse(
            TravelPlanFixtures.qualityResponse().days().stream()
                .map(
                    day ->
                        new TravelPlanAiResponse.Day(
                            day.date(),
                            day.places().stream()
                                .map(
                                    p ->
                                        p.providerPlaceId().equals("126144")
                                            ? new TravelPlanAiResponse.Place(
                                                p.order(),
                                                p.provider(),
                                                p.providerPlaceId(),
                                                "광안리해수욕장은 해변에서 다양한 활동과 바다 경치를 즐길 수 있는 곳입니다.")
                                            : p)
                                .toList()))
                .toList());
    assertThat(
            validator.feedback(
                response, planner.plan(TravelPlanFixtures.travel(), catalog), catalog))
        .singleElement()
        .asString()
        .contains("다른 장소의 설명");
  }
}
