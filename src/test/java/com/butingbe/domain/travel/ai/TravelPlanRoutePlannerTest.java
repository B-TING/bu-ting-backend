package com.butingbe.domain.travel.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.travel.dto.request.AiTravelPlanGenerateReqDto.WizardPickedPlaceReqDto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TravelPlanRoutePlannerTest {
  private final TravelPlanRoutePlanner planner = new TravelPlanRoutePlanner();

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
}
