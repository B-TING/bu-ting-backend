package com.butingbe.domain.travel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.ai.TravelPlanFixtures;
import com.butingbe.domain.travel.ai.TravelPlanValidationException;
import com.butingbe.domain.travel.dto.response.TravelPlansResDto;
import com.butingbe.domain.travel.service.AiTravelPlanService;
import com.butingbe.domain.travel.service.TravelService;
import com.butingbe.global.error.GlobalExceptionHandler;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@ExtendWith(RestDocumentationExtension.class)
class AiTravelPlanControllerTest {
  private final AiTravelPlanService service = mock(AiTravelPlanService.class);
  private final UUID travelId = UUID.randomUUID();
  private MockMvc mvc;
  private final tools.jackson.databind.json.JsonMapper mapper =
      new tools.jackson.databind.json.JsonMapper();

  @BeforeEach
  void setup(RestDocumentationContextProvider docs) {
    var messages = new ResourceBundleMessageSource();
    messages.setBasename("messages");
    messages.setDefaultEncoding("UTF-8");
    mvc =
        MockMvcBuilders.standaloneSetup(new TravelController(mock(TravelService.class), service))
            .setControllerAdvice(
                new GlobalExceptionHandler(messages, new AcceptHeaderLocaleResolver()))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setCustomHandlerMapping(
                () -> {
                  var mapping = new RequestMappingHandlerMapping();
                  mapping.setPathPrefixes(Map.of("/api/v1", c -> true));
                  return mapping;
                })
            .apply(documentationConfiguration(docs))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(UUID.randomUUID(), null, null, List.of()), null, List.of()));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void responseContractAndRealPrefix() throws Exception {
    when(service.generate(any(), any(), any()))
        .thenReturn(new TravelPlansResDto(travelId, "부산", List.of()));
    mvc.perform(
            post("/api/v1/travels/{travelId}/ai-plans", travelId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(TravelPlanFixtures.request())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.travelId").value(travelId.toString()))
        .andExpect(jsonPath("$.title").value("부산"))
        .andExpect(jsonPath("$.days").isArray())
        .andDo(document("travel-ai-plans-generate"));
  }

  @ParameterizedTest
  @EnumSource(TravelPlanValidationException.Reason.class)
  void invalidLlmOutputReturns502WithoutLeakingRawIds(TravelPlanValidationException.Reason reason)
      throws Exception {
    when(service.generate(any(), any(), any()))
        .thenThrow(new TravelPlanValidationException(reason, true, Set.of()));
    mvc.perform(
            post("/api/v1/travels/{travelId}/ai-plans", travelId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(TravelPlanFixtures.request())))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").isString());
  }

  @Test
  void invalidInputReferenceReturns400() throws Exception {
    when(service.generate(any(), any(), any()))
        .thenThrow(
            new TravelPlanValidationException(
                TravelPlanValidationException.Reason.INVALID_PLACE_REFERENCE, false, Set.of()));
    mvc.perform(
            post("/api/v1/travels/{travelId}/ai-plans", travelId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(TravelPlanFixtures.request())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void authenticationRequired() throws Exception {
    SecurityContextHolder.clearContext();
    mvc.perform(
            post("/api/v1/travels/{travelId}/ai-plans", travelId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(TravelPlanFixtures.request())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void emptySelectionRejected() throws Exception {
    mvc.perform(
            post("/api/v1/travels/{travelId}/ai-plans", travelId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedPlaces\":[]}"))
        .andExpect(status().isBadRequest());
  }
}
