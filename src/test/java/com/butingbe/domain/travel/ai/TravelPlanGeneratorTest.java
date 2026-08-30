package com.butingbe.domain.travel.ai;

import static com.butingbe.domain.travel.ai.TravelPlanFixtures.qualityResponse;
import static com.butingbe.domain.travel.ai.TravelPlanFixtures.request;
import static com.butingbe.domain.travel.ai.TravelPlanFixtures.travel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TravelPlanGeneratorTest {
  private final TravelPlanAiClient client = mock(TravelPlanAiClient.class);
  private final TravelPlanRoutePlanner planner = new TravelPlanRoutePlanner();
  private final TravelPlanGenerator generator =
      new TravelPlanGenerator(
          new TravelPlanPromptBuilder(),
          client,
          new TravelPlanAiResponseValidator(),
          planner,
          new TravelPlanQualityValidator(planner));

  @Test
  void returnsValidResultWithoutRetry() {
    when(client.generate(anyString())).thenReturn(qualityResponse());
    assertThat(generator.generate(travel(), request(), SelectedPlaceCatalog.from(request())))
        .isEqualTo(qualityResponse());
    verify(client).generate(anyString());
  }

  @Test
  void retriesPoorMemoOnceWithActionableFeedback() {
    when(client.generate(anyString())).thenReturn(poor(), qualityResponse());
    assertThat(generator.generate(travel(), request(), SelectedPlaceCatalog.from(request())))
        .isEqualTo(qualityResponse());
    var prompts = ArgumentCaptor.forClass(String.class);
    verify(client, times(2)).generate(prompts.capture());
    assertThat(prompts.getAllValues().get(0)).contains("날짜별 장소 묶음", "좌표 없는 장소");
    assertThat(prompts.getAllValues().get(1)).contains("직전 결과의 품질 검증 실패", "order=1", "반복 설명");
  }

  @Test
  void secondResultStillMustPassIdentityValidation() {
    when(client.generate(anyString()))
        .thenReturn(poor(), TravelPlanFixtures.response(TravelPlanFixtures.IDS.subList(0, 6)));
    assertThatThrownBy(
            () -> generator.generate(travel(), request(), SelectedPlaceCatalog.from(request())))
        .isInstanceOfSatisfying(
            TravelPlanValidationException.class,
            e ->
                assertThat(e.getReason())
                    .isEqualTo(TravelPlanValidationException.Reason.MISSING_SELECTED_PLACE));
    verify(client, times(2)).generate(anyString());
  }

  @Test
  void doesNotRetryTransportFailures() {
    when(client.generate(anyString())).thenThrow(new IllegalStateException("Unavailable"));
    assertThatThrownBy(
            () -> generator.generate(travel(), request(), SelectedPlaceCatalog.from(request())))
        .isInstanceOf(IllegalStateException.class);
    verify(client).generate(anyString());
  }

  private TravelPlanAiResponse poor() {
    return new TravelPlanAiResponse(
        qualityResponse().days().stream()
            .map(
                day ->
                    new TravelPlanAiResponse.Day(
                        day.date(),
                        day.places().stream()
                            .map(
                                p ->
                                    new TravelPlanAiResponse.Place(
                                        p.order(), p.provider(), p.providerPlaceId(), "추천 이유"))
                            .toList()))
            .toList());
  }
}
