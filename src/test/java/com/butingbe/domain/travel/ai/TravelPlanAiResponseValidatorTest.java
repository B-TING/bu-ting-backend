package com.butingbe.domain.travel.ai;

import static com.butingbe.domain.travel.ai.TravelPlanFixtures.IDS;
import static com.butingbe.domain.travel.ai.TravelPlanFixtures.request;
import static com.butingbe.domain.travel.ai.TravelPlanFixtures.response;
import static com.butingbe.domain.travel.ai.TravelPlanFixtures.travel;
import static com.butingbe.domain.travel.ai.TravelPlanValidationException.Reason.DUPLICATED_PLACE;
import static com.butingbe.domain.travel.ai.TravelPlanValidationException.Reason.INVALID_PLACE_REFERENCE;
import static com.butingbe.domain.travel.ai.TravelPlanValidationException.Reason.INVALID_SCHEDULE;
import static com.butingbe.domain.travel.ai.TravelPlanValidationException.Reason.MISSING_SELECTED_PLACE;
import static com.butingbe.domain.travel.ai.TravelPlanValidationException.Reason.UNEXPECTED_PLACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TravelPlanAiResponseValidatorTest {
  private final TravelPlanAiResponseValidator validator = new TravelPlanAiResponseValidator();

  @Test
  void balancedEightPlacesAcrossThreeDays() {
    var result = response(IDS);
    validator.validate(travel(), result, SelectedPlaceCatalog.from(request()));
    assertThat(result.days()).extracting(day -> day.places().size()).containsExactly(2, 3, 3);
    assertThat(
            result.days().stream()
                .flatMap(day -> day.places().stream())
                .map(p -> p.providerPlaceId()))
        .containsExactlyElementsOf(IDS)
        .doesNotHaveDuplicates();
  }

  @Test
  void detectsBothMissingContentIds() {
    assertThatThrownBy(() -> validate(response(IDS.subList(0, 6))))
        .isInstanceOfSatisfying(
            TravelPlanValidationException.class,
            e -> {
              assertThat(e.getReason()).isEqualTo(MISSING_SELECTED_PLACE);
              assertThat(e.getPlaceKeys())
                  .containsExactlyInAnyOrder(
                      PlaceKey.of("GOOGLE", "126083"), PlaceKey.of("GOOGLE", "127537"));
            });
  }

  @Test
  void rejectsUnexpectedContentId() {
    var ids = new ArrayList<>(IDS);
    ids.set(7, "unselected");
    assertFailure(response(ids), UNEXPECTED_PLACE);
  }

  @Test
  void rejectsDuplicateAcrossDays() {
    var ids = new ArrayList<>(IDS);
    ids.set(7, IDS.get(0));
    assertFailure(response(ids), DUPLICATED_PLACE);
  }

  @Test
  void providerIsPartOfIdentity() {
    var result = response(IDS);
    var days = new ArrayList<>(result.days());
    var places = new ArrayList<>(days.get(0).places());
    places.set(0, new TravelPlanAiResponse.Place(1, "NAVER", IDS.get(0), "memo"));
    days.set(0, new TravelPlanAiResponse.Day(days.get(0).date(), places));
    assertFailure(new TravelPlanAiResponse(days), UNEXPECTED_PLACE);
  }

  @Test
  void rejectsInvalidReference() {
    var days = new ArrayList<>(response(IDS).days());
    days.set(
        0,
        new TravelPlanAiResponse.Day(
            days.get(0).date(),
            List.of(new TravelPlanAiResponse.Place(1, "GOOGLE", null, "memo"))));
    assertFailure(new TravelPlanAiResponse(days), INVALID_PLACE_REFERENCE);
  }

  @Test
  void rejectsMissingDates() {
    assertFailure(new TravelPlanAiResponse(response(IDS).days().subList(0, 2)), INVALID_SCHEDULE);
  }

  @Test
  void rejectsNonContinuousOrder() {
    var days = new ArrayList<>(response(IDS).days());
    days.set(
        0,
        new TravelPlanAiResponse.Day(
            days.get(0).date(),
            List.of(new TravelPlanAiResponse.Place(2, "GOOGLE", IDS.get(0), "memo"))));
    assertFailure(new TravelPlanAiResponse(days), INVALID_SCHEDULE);
  }

  @Test
  @DisplayName("날짜가 없거나 중복이거나 여행 기간을 벗어나면 일정으로 인정하지 않는다")
  void rejectsInvalidDays() {
    var valid = response(IDS);

    assertFailure(withReplacedDay(valid, 1, null), INVALID_SCHEDULE);
    assertFailure(withReplacedDay(valid, 1, valid.days().get(0).date()), INVALID_SCHEDULE);
    assertFailure(withReplacedDay(valid, 1, java.time.LocalDate.of(1999, 1, 1)), INVALID_SCHEDULE);
  }

  private TravelPlanAiResponse withReplacedDay(
      TravelPlanAiResponse source, int index, java.time.LocalDate date) {
    var days = new ArrayList<>(source.days());
    days.set(index, new TravelPlanAiResponse.Day(date, days.get(index).places()));
    return new TravelPlanAiResponse(days);
  }

  private void validate(TravelPlanAiResponse result) {
    validator.validate(travel(), result, SelectedPlaceCatalog.from(request()));
  }

  private void assertFailure(
      TravelPlanAiResponse result, TravelPlanValidationException.Reason reason) {
    assertThatThrownBy(() -> validate(result))
        .isInstanceOfSatisfying(
            TravelPlanValidationException.class,
            e -> {
              assertThat(e.getReason()).isEqualTo(reason);
              assertThat(e.isGeneratedResponse()).isTrue();
            });
  }
}
