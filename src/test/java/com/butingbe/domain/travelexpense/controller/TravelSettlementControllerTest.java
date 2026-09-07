package com.butingbe.domain.travelexpense.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelexpense.dto.response.TravelSettlementResponse;
import com.butingbe.domain.travelexpense.service.TravelSettlementService;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class TravelSettlementControllerTest {

  private static final UUID TRAVEL_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");
  private static final UUID RECEIVER_ID = UUID.fromString("22222222-0000-0000-0000-000000000002");

  private MockMvc mockMvc;

  @Mock private TravelSettlementService travelSettlementService;

  @InjectMocks private TravelSettlementController travelSettlementController;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(travelSettlementController)
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(
                new MappingJackson2HttpMessageConverter(
                    new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()))
            .build();
  }

  @Test
  @DisplayName("정산 조회는 아직 확정되지 않은 미리보기를 반환한다")
  void getSettlementReturnsPreview() throws Exception {
    when(travelSettlementService.getSettlement(any(), any()))
        .thenReturn(
            TravelSettlementResponse.preview(
                TRAVEL_ID,
                List.of(
                    new TravelSettlementResponse.Transfer(
                        "KRW", USER_ID, "보내는사람", RECEIVER_ID, "받는사람", 15000L))));

    mockMvc
        .perform(get("/travels/{travelId}/expenses/settlements", TRAVEL_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.travelId").value(TRAVEL_ID.toString()))
        .andExpect(jsonPath("$.confirmed").value(false))
        .andExpect(jsonPath("$.confirmedById").doesNotExist())
        .andExpect(jsonPath("$.transfers[0].currency").value("KRW"))
        .andExpect(jsonPath("$.transfers[0].senderNickname").value("보내는사람"))
        .andExpect(jsonPath("$.transfers[0].amount").value(15000));
  }

  @Test
  @DisplayName("정산 확정은 확정 정보가 채워진 응답을 반환한다")
  void confirmSettlementReturnsConfirmedResponse() throws Exception {
    LocalDateTime confirmedAt = LocalDateTime.of(2026, 9, 5, 12, 0);
    when(travelSettlementService.confirmSettlement(any(), any()))
        .thenReturn(
            new TravelSettlementResponse(
                TRAVEL_ID,
                true,
                USER_ID,
                confirmedAt,
                List.of(
                    new TravelSettlementResponse.Transfer(
                        "KRW", USER_ID, "보내는사람", RECEIVER_ID, "받는사람", 15000L))));

    mockMvc
        .perform(post("/travels/{travelId}/expenses/settlements/confirm", TRAVEL_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.confirmed").value(true))
        .andExpect(jsonPath("$.confirmedById").value(USER_ID.toString()))
        .andExpect(jsonPath("$.transfers[0].receiverId").value(RECEIVER_ID.toString()));
  }

  @Test
  @DisplayName("인증되지 않은 사용자가 정산을 조회하면 UnauthenticatedException을 던진다")
  void getSettlementRejectsUnauthenticatedUser() {
    assertThatThrownBy(() -> travelSettlementController.getSettlement(null, TRAVEL_ID))
        .isInstanceOf(UnauthenticatedException.class);
  }

  @Test
  @DisplayName("인증되지 않은 사용자가 정산을 확정하면 UnauthenticatedException을 던진다")
  void confirmSettlementRejectsUnauthenticatedUser() {
    assertThatThrownBy(() -> travelSettlementController.confirmSettlement(null, TRAVEL_ID))
        .isInstanceOf(UnauthenticatedException.class);
  }

  private HandlerMethodArgumentResolver authenticatedUserResolver() {
    return new HandlerMethodArgumentResolver() {
      @Override
      public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
      }

      @Override
      public Object resolveArgument(
          MethodParameter parameter,
          ModelAndViewContainer mavContainer,
          NativeWebRequest webRequest,
          WebDataBinderFactory binderFactory) {
        return new AuthenticatedUser(USER_ID, "user@example.com", "tester", List.of());
      }
    };
  }
}
