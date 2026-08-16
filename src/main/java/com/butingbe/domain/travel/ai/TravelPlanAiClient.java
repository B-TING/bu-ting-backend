package com.butingbe.domain.travel.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class TravelPlanAiClient {
  private final ChatClient chatClient;

  public TravelPlanAiClient(ChatClient.Builder chatClientBuilder) {
    this.chatClient = chatClientBuilder.build();
  }

  public TravelPlanAiResponse generate(String prompt) {
    try {
      TravelPlanAiResponse response =
          chatClient.prompt().user(prompt).call().entity(TravelPlanAiResponse.class);
      if (response == null) {
        throw new IllegalStateException("AI response is empty.");
      }
      return response;
    } catch (RuntimeException e) {
      throw new IllegalStateException("AI travel plan generation failed.", e);
    }
  }
}
