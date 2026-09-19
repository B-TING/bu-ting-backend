package com.butingbe.domain.travel.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto.WizardPickedPlaceReqDto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TravelPlanRoutePlannerTest {
  private final TravelPlanRoutePlanner planner =
      com.butingbe.domain.travel.ai.TravelPlanFixtures.routePlanner();

  @Test
  void clustersBusanPlacesWithoutDroppingAnyAndImprovesObservedMixedRoute() {
    double[] lat = {35.0974, 35.1587, 35.1532, 35.1885, 35.0977, 35.0517, 35.181, 35.101};
    double[] lon = {129.0107, 129.1604, 129.1186, 129.2233, 129.0307, 129.085, 129.207, 129.032};
    var places = new ArrayList<WizardPickedPlaceReqDto>();
    for (int i = 0; i < 8; i++) {
      var original = TravelPlanFixtures.request().selectedPlaces().get(i);
      places.add(
          new WizardPickedPlaceReqDto(
              "GOOGLE",
              original.providerPlaceId(),
              original.placeName(),
              original.address(),
              lat[i],
              lon[i],
              original.type()));
    }
    var catalog = SelectedPlaceCatalog.from(TravelPlanFixtures.request(places));
    var routes = planner.plan(TravelPlanFixtures.travel(), catalog);
    assertThat(routes).hasSize(3);
    assertThat(routes.values().stream().flatMap(List::stream).toList())
        .hasSize(8)
        .doesNotHaveDuplicates()
        .containsExactlyInAnyOrderElementsOf(catalog.keySet());
    double before =
        List.of(
                List.of("266143", "126081", "127784"),
                List.of("2564951", "126144", "126083"),
                List.of("127537", "126760"))
            .stream()
            .mapToDouble(
                ids ->
                    planner.length(
                        ids.stream().map(id -> PlaceKey.of("GOOGLE", id)).toList(), catalog))
            .sum();
    double after =
        routes.values().stream().mapToDouble(keys -> planner.length(keys, catalog)).sum();
    assertThat(after).isLessThan(before);
    java.util.Collections.reverse(places);
    assertThat(
            planner.plan(
                TravelPlanFixtures.travel(),
                SelectedPlaceCatalog.from(TravelPlanFixtures.request(places))))
        .isEqualTo(routes);
  }

  @Test
  void retainsMissingCoordinatesAndAllowsEmptyDays() {
    var unknown =
        new WizardPickedPlaceReqDto("GOOGLE", "1", "장소", "주소", null, null, "TOURIST_SPOT");
    var known =
        new WizardPickedPlaceReqDto("GOOGLE", "2", "장소2", "주소2", 35.0, 129.0, "TOURIST_SPOT");
    var catalog = SelectedPlaceCatalog.from(TravelPlanFixtures.request(List.of(unknown, known)));
    var routes = planner.plan(TravelPlanFixtures.travel(), catalog);
    assertThat(routes.values().stream().flatMap(List::stream).toList()).hasSize(2);
    assertThat(routes.values()).anyMatch(List::isEmpty);
    assertThat(planner.located(unknown)).isFalse();
  }

  @Test
  void rejectsReversedDatesBeforeGeneration() {
    var travel =
        com.butingbe.domain.travel.entity.Travel.builder()
            .startDate(TravelPlanFixtures.START)
            .endDate(TravelPlanFixtures.START.minusDays(1))
            .build();
    assertThatThrownBy(
            () -> planner.plan(travel, SelectedPlaceCatalog.from(TravelPlanFixtures.request())))
        .isInstanceOf(TravelPlanValidationException.class);
  }

  @Test
  @org.junit.jupiter.api.DisplayName("하루 예산을 넘긴 장소는 다음 날로 넘어간다")
  void carriesOverPlacesThatExceedDailyBudget() {
    // 체류 시간 300분짜리 장소는 RELAXED(480분) 하루에 하나밖에 들어가지 않는다.
    var planner =
        new TravelPlanRoutePlanner(
            new com.butingbe.domain.route.VisitOrderOptimizer(
                new com.butingbe.domain.route.HaversineRouteProvider()),
            new com.butingbe.domain.route.HaversineRouteProvider(),
            (provider, providerPlaceId) -> 300,
            (provider, providerPlaceId) -> java.util.Optional.empty());
    var places = new ArrayList<WizardPickedPlaceReqDto>();
    for (int i = 0; i < 4; i++) {
      places.add(
          new WizardPickedPlaceReqDto(
              "GOOGLE",
              String.valueOf(i),
              "장소" + i,
              "주소",
              35.0 + i / 1000.0,
              129.0,
              "TOURIST_SPOT"));
    }
    var catalog = SelectedPlaceCatalog.from(TravelPlanFixtures.request(places));
    var travel =
        com.butingbe.domain.travel.entity.Travel.builder()
            .destination("부산")
            .startDate(TravelPlanFixtures.START)
            .endDate(TravelPlanFixtures.START.plusDays(1))
            .pace(com.butingbe.domain.travel.entity.TravelPace.RELAXED)
            .build();

    var routes = planner.plan(travel, catalog);

    assertThat(routes.get(TravelPlanFixtures.START)).hasSize(1);
    assertThat(routes.get(TravelPlanFixtures.START.plusDays(1))).hasSize(3);
    assertThat(routes.values().stream().flatMap(List::stream).toList())
        .containsExactlyInAnyOrderElementsOf(catalog.keySet());
  }

  @Test
  @org.junit.jupiter.api.DisplayName("여행 속도가 빠르면 하루에 더 많이 담는다")
  void tighterPaceFitsMorePerDay() {
    var planner =
        new TravelPlanRoutePlanner(
            new com.butingbe.domain.route.VisitOrderOptimizer(
                new com.butingbe.domain.route.HaversineRouteProvider()),
            new com.butingbe.domain.route.HaversineRouteProvider(),
            (provider, providerPlaceId) -> 300,
            (provider, providerPlaceId) -> java.util.Optional.empty());
    var places = new ArrayList<WizardPickedPlaceReqDto>();
    for (int i = 0; i < 4; i++) {
      places.add(
          new WizardPickedPlaceReqDto(
              "GOOGLE",
              String.valueOf(i),
              "장소" + i,
              "주소",
              35.0 + i / 1000.0,
              129.0,
              "TOURIST_SPOT"));
    }
    var catalog = SelectedPlaceCatalog.from(TravelPlanFixtures.request(places));
    var tight =
        com.butingbe.domain.travel.entity.Travel.builder()
            .destination("부산")
            .startDate(TravelPlanFixtures.START)
            .endDate(TravelPlanFixtures.START.plusDays(1))
            .pace(com.butingbe.domain.travel.entity.TravelPace.TIGHT)
            .build();

    // 같은 입력이 RELAXED에서는 하루에 하나였다(위 테스트).
    assertThat(planner.plan(tight, catalog).get(TravelPlanFixtures.START)).hasSize(2);
  }

  @Test
  @org.junit.jupiter.api.DisplayName("마지막 날은 예산을 넘겨도 장소를 빼지 않는다")
  void keepsOverflowOnLastDay() {
    var planner =
        new TravelPlanRoutePlanner(
            new com.butingbe.domain.route.VisitOrderOptimizer(
                new com.butingbe.domain.route.HaversineRouteProvider()),
            new com.butingbe.domain.route.HaversineRouteProvider(),
            (provider, providerPlaceId) -> 600,
            (provider, providerPlaceId) -> java.util.Optional.empty());
    var places =
        List.of(
            new WizardPickedPlaceReqDto("GOOGLE", "1", "장소1", "주소", 35.0, 129.0, "TOURIST_SPOT"),
            new WizardPickedPlaceReqDto("GOOGLE", "2", "장소2", "주소", 35.001, 129.0, "TOURIST_SPOT"));
    var catalog = SelectedPlaceCatalog.from(TravelPlanFixtures.request(places));
    var oneDay =
        com.butingbe.domain.travel.entity.Travel.builder()
            .destination("부산")
            .startDate(TravelPlanFixtures.START)
            .endDate(TravelPlanFixtures.START)
            .pace(com.butingbe.domain.travel.entity.TravelPace.RELAXED)
            .build();

    assertThat(planner.plan(oneDay, catalog).get(TravelPlanFixtures.START)).hasSize(2);
  }

  @Test
  @org.junit.jupiter.api.DisplayName("좌표 없는 장소는 이동 시간을 0으로 보고 예산에 체류 시간만 더한다")
  void treatsUnlocatedPlaceAsNoTravel() {
    var planner =
        new TravelPlanRoutePlanner(
            new com.butingbe.domain.route.VisitOrderOptimizer(
                new com.butingbe.domain.route.HaversineRouteProvider()),
            new com.butingbe.domain.route.HaversineRouteProvider(),
            (provider, providerPlaceId) -> 100,
            (provider, providerPlaceId) -> java.util.Optional.empty());
    // 좌표 있는 장소 둘이 각각 하루를 차지하고, 좌표 없는 장소가 그중 한 날에 덧붙는다.
    var first =
        new WizardPickedPlaceReqDto("GOOGLE", "1", "장소1", "주소", 35.0, 129.0, "TOURIST_SPOT");
    var second =
        new WizardPickedPlaceReqDto("GOOGLE", "2", "장소2", "주소", 35.3, 129.3, "TOURIST_SPOT");
    var unlocated =
        new WizardPickedPlaceReqDto("GOOGLE", "3", "장소3", "주소", null, null, "TOURIST_SPOT");
    var catalog =
        SelectedPlaceCatalog.from(TravelPlanFixtures.request(List.of(first, second, unlocated)));
    var travel =
        com.butingbe.domain.travel.entity.Travel.builder()
            .destination("부산")
            .startDate(TravelPlanFixtures.START)
            .endDate(TravelPlanFixtures.START.plusDays(1))
            .pace(com.butingbe.domain.travel.entity.TravelPace.RELAXED)
            .build();

    var routes = planner.plan(travel, catalog);

    assertThat(routes.values().stream().flatMap(List::stream).toList())
        .containsExactlyInAnyOrderElementsOf(catalog.keySet());
  }

  @Test
  @org.junit.jupiter.api.DisplayName("야경 장소는 그날 마지막, 오전 장소는 처음으로 간다")
  void movesTimeSlotPlacesToEdges() {
    // 최적 순서에서 가운데 오던 장소들이 시간대 때문에 앞뒤로 밀려야 한다.
    var slots =
        java.util.Map.of(
            "morning", com.butingbe.domain.place.entity.PlaceTimeSlot.MORNING,
            "evening", com.butingbe.domain.place.entity.PlaceTimeSlot.EVENING);
    var planner =
        new TravelPlanRoutePlanner(
            new com.butingbe.domain.route.VisitOrderOptimizer(
                new com.butingbe.domain.route.HaversineRouteProvider()),
            new com.butingbe.domain.route.HaversineRouteProvider(),
            (provider, providerPlaceId) -> 30,
            (provider, providerPlaceId) ->
                java.util.Optional.ofNullable(slots.get(providerPlaceId)));
    var places =
        List.of(
            new WizardPickedPlaceReqDto(
                "GOOGLE", "evening", "광안리", "주소", 35.1532, 129.1186, "TOURIST_SPOT"),
            new WizardPickedPlaceReqDto(
                "GOOGLE", "plain", "그냥 장소", "주소", 35.1535, 129.119, "TOURIST_SPOT"),
            new WizardPickedPlaceReqDto(
                "GOOGLE", "morning", "자갈치", "주소", 35.1538, 129.1194, "TOURIST_SPOT"));
    var catalog = SelectedPlaceCatalog.from(TravelPlanFixtures.request(places));
    var oneDay =
        com.butingbe.domain.travel.entity.Travel.builder()
            .destination("부산")
            .startDate(TravelPlanFixtures.START)
            .endDate(TravelPlanFixtures.START)
            .pace(com.butingbe.domain.travel.entity.TravelPace.TIGHT)
            .build();

    var ordered = planner.plan(oneDay, catalog).get(TravelPlanFixtures.START);

    assertThat(ordered.get(0)).isEqualTo(PlaceKey.of("GOOGLE", "morning"));
    assertThat(ordered.get(ordered.size() - 1)).isEqualTo(PlaceKey.of("GOOGLE", "evening"));
  }

  @Test
  @org.junit.jupiter.api.DisplayName("시간대가 지정되지 않으면 최적화 순서를 그대로 둔다")
  void keepsOptimizedOrderWithoutTimeSlots() {
    var withSlots = TravelPlanFixtures.routePlanner();
    var places =
        List.of(
            new WizardPickedPlaceReqDto("GOOGLE", "1", "장소1", "주소", 35.10, 129.10, "TOURIST_SPOT"),
            new WizardPickedPlaceReqDto("GOOGLE", "2", "장소2", "주소", 35.11, 129.11, "TOURIST_SPOT"),
            new WizardPickedPlaceReqDto("GOOGLE", "3", "장소3", "주소", 35.12, 129.12, "TOURIST_SPOT"));
    var catalog = SelectedPlaceCatalog.from(TravelPlanFixtures.request(places));
    var oneDay =
        com.butingbe.domain.travel.entity.Travel.builder()
            .destination("부산")
            .startDate(TravelPlanFixtures.START)
            .endDate(TravelPlanFixtures.START)
            .pace(com.butingbe.domain.travel.entity.TravelPace.TIGHT)
            .build();

    assertThat(withSlots.plan(oneDay, catalog).get(TravelPlanFixtures.START)).hasSize(3);
  }
}
