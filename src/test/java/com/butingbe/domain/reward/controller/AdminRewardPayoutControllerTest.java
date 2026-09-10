package com.butingbe.domain.reward.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.dto.response.AdminRewardPayoutBulkResultResDto;
import com.butingbe.domain.reward.dto.response.AdminRewardPayoutDetailResDto;
import com.butingbe.domain.reward.dto.response.AdminRewardPayoutPageResDto;
import com.butingbe.domain.reward.dto.response.AdminRewardPayoutReleaseHoldResDto;
import com.butingbe.domain.reward.service.AdminRewardPayoutService;
import com.butingbe.global.error.GlobalExceptionHandler;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

@ExtendWith(MockitoExtension.class)
class AdminRewardPayoutControllerTest {

  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private AdminRewardPayoutService adminRewardPayoutService;
  @InjectMocks private AdminRewardPayoutController controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    StaticMessageSource messageSource = new StaticMessageSource();
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .setValidator(validator)
            .setControllerAdvice(
                new GlobalExceptionHandler(messageSource, new FixedLocaleResolver(Locale.KOREAN)))
            .build();
  }

  @Test
  @DisplayName("지급 목록 조회 200")
  void list() throws Exception {
    when(adminRewardPayoutService.list(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new AdminRewardPayoutPageResDto(List.of(), 1, 20, 0, 1, false));

    mockMvc
        .perform(get("/admin/reward-payouts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray());
  }

  @Test
  @DisplayName("지급 상세 조회 200")
  void detail() throws Exception {
    UUID payoutId = UUID.randomUUID();
    when(adminRewardPayoutService.detail(any(), eq(payoutId)))
        .thenReturn(
            new AdminRewardPayoutDetailResDto(
                payoutId.toString(), "TOP_LIKE", UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 1, 3L, null, "PENDING_ASSIGN", "NONE",
                null, null, null, null, null, null, null, null, null, null, 0L, null, null));

    mockMvc
        .perform(get("/admin/reward-payouts/{id}", payoutId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.payoutType").value("TOP_LIKE"));
  }

  @Test
  @DisplayName("보류 해제 200")
  void releaseHold() throws Exception {
    UUID payoutId = UUID.randomUUID();
    when(adminRewardPayoutService.releaseHold(any(), eq(payoutId), any(), any()))
        .thenReturn(
            new AdminRewardPayoutReleaseHoldResDto(payoutId.toString(), "TOP_LIKE", "NONE", 1L));
    mockMvc
        .perform(
            post("/admin/reward-payouts/{id}/release-hold", payoutId)
                .contentType("application/json")
                .content("{\"note\":\"최종 확인\",\"expectedRevision\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.holdStatus").value("NONE"));
  }

  @Test
  @DisplayName("note·expectedRevision이 없으면 400")
  void releaseHoldValidation() throws Exception {
    mockMvc
        .perform(
            post("/admin/reward-payouts/{id}/release-hold", UUID.randomUUID())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("지급 수정 200")
  void update() throws Exception {
    UUID payoutId = UUID.randomUUID();
    when(adminRewardPayoutService.update(any(), eq(payoutId), any(), any()))
        .thenReturn(
            new AdminRewardPayoutDetailResDto(
                payoutId.toString(), "TOP_LIKE", UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 1, 3L, null, "PENDING_CONFIRM", "NONE",
                null, null, null, null, null, null, null, null, "메모", null, 1L, null, null));

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/admin/reward-payouts/{id}", payoutId)
                .contentType("application/json")
                .content("{\"expectedRevision\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.memo").value("메모"));
  }

  @Test
  @DisplayName("expectedRevision이 없으면 400")
  void updateValidation() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/admin/reward-payouts/{id}", UUID.randomUUID())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("일괄 확정 200")
  void bulkConfirm() throws Exception {
    when(adminRewardPayoutService.bulkConfirm(any(), any(), any()))
        .thenReturn(new AdminRewardPayoutBulkResultResDto(List.of()));

    mockMvc
        .perform(
            post("/admin/reward-payouts/bulk-confirm")
                .contentType("application/json")
                .content(
                    "{\"payoutIds\":[\"" + UUID.randomUUID() + "\"],\"expectedRevisions\":{}}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("일괄 확정: payoutIds가 비어있으면 400")
  void bulkConfirmValidation() throws Exception {
    mockMvc
        .perform(
            post("/admin/reward-payouts/bulk-confirm")
                .contentType("application/json")
                .content("{\"payoutIds\":[],\"expectedRevisions\":{}}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("일괄 확정: expectedRevisions가 없으면 400")
  void bulkConfirmMissingExpectedRevisionsValidation() throws Exception {
    mockMvc
        .perform(
            post("/admin/reward-payouts/bulk-confirm")
                .contentType("application/json")
                .content("{\"payoutIds\":[\"" + UUID.randomUUID() + "\"]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("일괄 일정 200")
  void bulkSchedule() throws Exception {
    when(adminRewardPayoutService.bulkSchedule(any(), any(), any()))
        .thenReturn(new AdminRewardPayoutBulkResultResDto(List.of()));

    mockMvc
        .perform(
            post("/admin/reward-payouts/bulk-schedule")
                .contentType("application/json")
                .content(
                    "{\"payoutIds\":[\""
                        + UUID.randomUUID()
                        + "\"],\"scheduledAt\":\"2026-10-01T00:00:00Z\",\"expectedRevisions\":{}}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("메일 발송 기록 200")
  void markMailSent() throws Exception {
    UUID payoutId = UUID.randomUUID();
    when(adminRewardPayoutService.markMailSent(any(), any(), any()))
        .thenReturn(
            new AdminRewardPayoutDetailResDto(
                payoutId.toString(), "TOP_LIKE", UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 1, 3L, null, "MAIL_SENT", "NONE",
                null, null, null, null, null, null, null, null, null, null, 1L, null, null));

    mockMvc
        .perform(
            post("/admin/reward-payouts/mark-mail-sent")
                .contentType("application/json")
                .content(
                    "{\"payoutId\":\"" + payoutId + "\",\"note\":\"발송\",\"expectedRevision\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("MAIL_SENT"));
  }

  @Test
  @DisplayName("개인정보 수집 기록 200")
  void markInfoCollected() throws Exception {
    UUID payoutId = UUID.randomUUID();
    when(adminRewardPayoutService.markInfoCollected(any(), any(), any()))
        .thenReturn(
            new AdminRewardPayoutDetailResDto(
                payoutId.toString(), "TOP_LIKE", UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 1, 3L, null, "INFO_COLLECTED", "NONE",
                null, null, null, null, null, null, null, null, null, null, 1L, null, null));

    mockMvc
        .perform(
            post("/admin/reward-payouts/mark-info-collected")
                .contentType("application/json")
                .content(
                    "{\"payoutId\":\"" + payoutId + "\",\"note\":\"수집\",\"expectedRevision\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("INFO_COLLECTED"));
  }

  @Test
  @DisplayName("mark-mail-sent: payoutId·expectedRevision이 없으면 400")
  void markMailSentValidation() throws Exception {
    mockMvc
        .perform(
            post("/admin/reward-payouts/mark-mail-sent")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("발송 완료 기록 200")
  void markSent() throws Exception {
    UUID payoutId = UUID.randomUUID();
    when(adminRewardPayoutService.markSent(any(), any(), any()))
        .thenReturn(
            new AdminRewardPayoutDetailResDto(
                payoutId.toString(), "BASE", UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), null, null, null, "PAID", "NONE",
                null, null, null, null, null, null,
                java.time.OffsetDateTime.now(), null, null, null, 1L, null, null));

    mockMvc
        .perform(
            post("/admin/reward-payouts/mark-sent")
                .contentType("application/json")
                .content(
                    "{\"payoutId\":\"" + payoutId + "\",\"note\":\"완료\",\"expectedRevision\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PAID"));
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
        return new AuthenticatedUser(USER_ID, "op@example.com", "op", List.of());
      }
    };
  }
}
