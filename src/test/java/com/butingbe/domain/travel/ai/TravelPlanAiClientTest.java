package com.butingbe.domain.travel.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class TravelPlanAiClientTest {
  @Test
  void ignoresLlmIdentityMetadataAndParsesOnlyReferencesAndSchedule() {
    ChatModel model = mock(ChatModel.class);
    when(model.getOptions())
        .thenReturn(org.springframework.ai.chat.prompt.ChatOptions.builder().build());
    when(model.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(
                List.of(
                    new Generation(
                        new AssistantMessage(
                            """
        {"days":[{"date":"2026-09-01","places":[{"order":1,"provider":"GOOGLE",
        "providerPlaceId":"126083","memo":"해변 산책","name":"가짜 장소", "placeName":"부산타워",
        "address":"다른 주소","latitude":0,"longitude":0}]}]}
        """)))));
    var response = new TravelPlanAiClient(ChatClient.builder(model)).generate("계획을 생성하세요");
    var place = response.days().get(0).places().get(0);
    assertThat(place.providerPlaceId()).isEqualTo("126083");
    assertThat(place.provider()).isEqualTo("GOOGLE");
    assertThat(place.memo()).isEqualTo("해변 산책");
    assertThat(TravelPlanAiResponse.Place.class.getRecordComponents())
        .extracting(c -> c.getName())
        .containsExactly("order", "provider", "providerPlaceId", "memo");
  }

  @Test
  @DisplayName("AI 호출이 실패하면 원인을 감싸 IllegalStateException을 던진다")
  void wrapsCallFailure() {
    ChatModel model = mock(ChatModel.class);
    when(model.getOptions())
        .thenReturn(org.springframework.ai.chat.prompt.ChatOptions.builder().build());
    when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("upstream down"));

    TravelPlanAiClient client = new TravelPlanAiClient(ChatClient.builder(model));

    assertThatThrownBy(() -> client.generate("계획을 생성하세요"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("AI travel plan generation failed.")
        .hasRootCauseMessage("upstream down");
  }

  @Test
  @DisplayName("AI 응답을 일정으로 해석할 수 없으면 IllegalStateException을 던진다")
  void wrapsUnparseableResponse() {
    ChatModel model = mock(ChatModel.class);
    when(model.getOptions())
        .thenReturn(org.springframework.ai.chat.prompt.ChatOptions.builder().build());
    when(model.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(List.of(new Generation(new AssistantMessage("not json at all")))));

    TravelPlanAiClient client = new TravelPlanAiClient(ChatClient.builder(model));

    assertThatThrownBy(() -> client.generate("계획을 생성하세요"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("AI travel plan generation failed.");
  }

  @Test
  @DisplayName("AI가 응답 본문을 주지 않으면 빈 응답으로 판단해 실패시킨다")
  void rejectsNullEntity() {
    ChatClient.Builder builder = mock(ChatClient.Builder.class);
    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);

    when(builder.build()).thenReturn(chatClient);
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.user(anyString())).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callSpec);
    when(callSpec.entity(TravelPlanAiResponse.class)).thenReturn(null);

    TravelPlanAiClient client = new TravelPlanAiClient(builder);

    assertThatThrownBy(() -> client.generate("계획을 생성하세요"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("AI travel plan generation failed.")
        .hasRootCauseMessage("AI response is empty.");
  }
}
