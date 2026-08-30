package com.butingbe.domain.travel.service;

import static com.butingbe.domain.travel.ai.TravelPlanFixtures.IDS;
import static com.butingbe.domain.travel.ai.TravelPlanFixtures.qualityResponse;
import static com.butingbe.domain.travel.ai.TravelPlanFixtures.request;
import static com.butingbe.domain.travel.ai.TravelPlanFixtures.response;
import static com.butingbe.domain.travel.ai.TravelPlanFixtures.travel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.ai.TravelPlanAiClient;
import com.butingbe.domain.travel.ai.TravelPlanAiResponseValidator;
import com.butingbe.domain.travel.ai.TravelPlanGenerator;
import com.butingbe.domain.travel.ai.TravelPlanPromptBuilder;
import com.butingbe.domain.travel.ai.TravelPlanQualityValidator;
import com.butingbe.domain.travel.ai.TravelPlanRoutePlanner;
import com.butingbe.domain.travel.ai.TravelPlanValidationException;
import com.butingbe.domain.travel.entity.Plan;
import com.butingbe.domain.travel.entity.PlanPlace;
import com.butingbe.domain.travel.repository.PlanPlaceRepository;
import com.butingbe.domain.travel.repository.PlanRepository;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travelteam.service.TravelMemberAuthorization;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

class AiTravelPlanServiceTest {
  private final PlanRepository plans = mock(PlanRepository.class);
  private final PlanPlaceRepository places = mock(PlanPlaceRepository.class);
  private final TravelRepository travels = mock(TravelRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final TravelPlanAiClient ai = mock(TravelPlanAiClient.class);
  private final AiTravelPlanService service =
      new AiTravelPlanService(
          travels,
          plans,
          places,
          users,
          mock(TravelMemberAuthorization.class),
          new TravelPlanGenerator(
              new TravelPlanPromptBuilder(),
              ai,
              new TravelPlanAiResponseValidator(),
              new TravelPlanRoutePlanner(),
              new TravelPlanQualityValidator(new TravelPlanRoutePlanner())));
  private final UUID travelId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final AuthenticatedUser principal = new AuthenticatedUser(userId, null, null, List.of());

  @BeforeEach
  void setup() {
    var travel = travel();
    ReflectionTestUtils.setField(travel, "id", travelId);
    var user = mock(User.class);
    when(user.getId()).thenReturn(userId);
    when(travels.findById(travelId)).thenReturn(Optional.of(travel));
    when(users.findById(userId)).thenReturn(Optional.of(user));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void savesAllEightOriginalPlacesAndReturnsUnchangedApiContract(boolean tamperedMetadata) {
    if (tamperedMetadata) {
      ChatModel model = mock(ChatModel.class);
      when(model.getOptions())
          .thenReturn(org.springframework.ai.chat.prompt.ChatOptions.builder().build());
      String json =
          new tools.jackson.databind.json.JsonMapper().writeValueAsString(qualityResponse());
      json =
          json.replace(
              "\"memo\":",
              "\"placeName\":\"위조 이름\",\"address\":\"위조 주소\",\"latitude\":0,\"longitude\":0,\"memo\":");
      when(model.call(any(Prompt.class)))
          .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));
      when(ai.generate(anyString()))
          .thenAnswer(
              call ->
                  new TravelPlanAiClient(ChatClient.builder(model)).generate(call.getArgument(0)));
    } else {
      when(ai.generate(anyString())).thenReturn(qualityResponse());
    }
    List<PlanPlace> stored = new ArrayList<>();
    when(plans.save(any()))
        .thenAnswer(
            call -> {
              Plan plan = call.getArgument(0);
              ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
              return plan;
            });
    when(places.save(any()))
        .thenAnswer(
            call -> {
              PlanPlace place = call.getArgument(0);
              ReflectionTestUtils.setField(place, "id", UUID.randomUUID());
              stored.add(place);
              return place;
            });
    when(places.findByPlan_IdOrderBySequenceAsc(any()))
        .thenAnswer(
            call ->
                stored.stream()
                    .filter(p -> p.getPlan().getId().equals(call.getArgument(0)))
                    .toList());
    var result = service.generate(principal, travelId, request());
    assertThat(result.travelId()).isEqualTo(travelId);
    assertThat(result.days()).hasSize(3);
    var output = result.days().stream().flatMap(day -> day.places().stream()).toList();
    assertThat(output)
        .hasSize(8)
        .extracting(p -> p.providerPlaceId())
        .containsExactlyInAnyOrderElementsOf(IDS)
        .doesNotHaveDuplicates();
    for (int i = 0; i < 8; i++) {
      var original = request().selectedPlaces().get(i);
      var actual =
          output.stream()
              .filter(p -> p.providerPlaceId().equals(original.providerPlaceId()))
              .findFirst()
              .orElseThrow();
      assertThat(actual.placeName()).isEqualTo(original.placeName());
      assertThat(actual.address()).isEqualTo(original.address());
      assertThat(actual.latitude()).isEqualTo(original.latitude());
      assertThat(actual.longitude()).isEqualTo(original.longitude());
      assertThat(actual.provider().name()).isEqualTo(original.provider());
      assertThat(actual.providerPlaceId()).isEqualTo(original.providerPlaceId());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "unexpected", "duplicate", "invalid"})
  void invalidLlmResponseNeverCallsAnyPlanOrPlaceRepository(String scenario) {
    var ids = new ArrayList<>(IDS);
    switch (scenario) {
      case "missing" -> ids.subList(6, 8).clear();
      case "unexpected" -> ids.set(7, "not-selected");
      case "duplicate" -> ids.set(7, IDS.get(0));
      case "invalid" -> ids.set(7, " ");
      default -> throw new AssertionError(scenario);
    }
    when(ai.generate(anyString())).thenReturn(response(ids));
    assertThatThrownBy(() -> service.generate(principal, travelId, request()))
        .isInstanceOf(TravelPlanValidationException.class);
    verifyNoInteractions(plans, places);
  }

  @Test
  void duplicateInputIsRejectedBeforeLlmCall() {
    var input = new ArrayList<>(request().selectedPlaces());
    input.add(input.get(0));
    assertThatThrownBy(() -> service.generate(principal, travelId, request(input)))
        .isInstanceOfSatisfying(
            TravelPlanValidationException.class,
            e -> assertThat(e.isGeneratedResponse()).isFalse());
    verifyNoInteractions(ai, plans, places);
  }

  @Test
  void repeatedQualityFailureDoesNotWritePlans() {
    var poor =
        new com.butingbe.domain.travel.ai.TravelPlanAiResponse(
            qualityResponse().days().stream()
                .map(
                    day ->
                        new com.butingbe.domain.travel.ai.TravelPlanAiResponse.Day(
                            day.date(),
                            day.places().stream()
                                .map(
                                    p ->
                                        new com.butingbe.domain.travel.ai.TravelPlanAiResponse
                                            .Place(
                                            p.order(), p.provider(), p.providerPlaceId(), "추천 이유"))
                                .toList()))
                .toList());
    when(ai.generate(anyString())).thenReturn(poor);
    assertThatThrownBy(() -> service.generate(principal, travelId, request()))
        .isInstanceOfSatisfying(
            TravelPlanValidationException.class,
            e ->
                assertThat(e.getReason())
                    .isEqualTo(TravelPlanValidationException.Reason.LOW_QUALITY_PLAN));
    org.mockito.Mockito.verify(ai, org.mockito.Mockito.times(2)).generate(anyString());
    verifyNoInteractions(plans, places);
  }
}
