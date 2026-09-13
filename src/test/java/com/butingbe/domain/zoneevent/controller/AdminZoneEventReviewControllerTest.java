package com.butingbe.domain.zoneevent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.AdminReviewQueuePageResDto;
import com.butingbe.domain.zoneevent.service.AdminZoneEventReviewService;
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
class AdminZoneEventReviewControllerTest {

  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private AdminZoneEventReviewService adminZoneEventReviewService;
  @InjectMocks private AdminZoneEventReviewController controller;

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
  @DisplayName("검수 큐 200")
  void queue() throws Exception {
    when(adminZoneEventReviewService.queue(any(), any(), any(), any(), any(), any()))
        .thenReturn(new AdminReviewQueuePageResDto(List.of(), 1, 20, 0, 0, false));
    mockMvc.perform(get("/admin/zone-event-reviews")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("검수 상세 200")
  void detail() throws Exception {
    UUID pid = UUID.randomUUID();
    when(adminZoneEventReviewService.detail(any(), eq(pid)))
        .thenReturn(
            new com.butingbe.domain.zoneevent.dto.response.AdminReviewDetailResDto(
                pid.toString(),
                null,
                null,
                "SUYEONG_NAMGU",
                UUID.randomUUID().toString(),
                "닉",
                "e@x.com",
                "UNDER_REVIEW",
                null,
                null,
                List.of(),
                java.time.OffsetDateTime.now()));
    mockMvc.perform(get("/admin/zone-event-reviews/{id}", pid)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("승인 200")
  void approve() throws Exception {
    UUID pid = UUID.randomUUID();
    when(adminZoneEventReviewService.approve(any(), eq(pid), any(), any()))
        .thenReturn(
            new com.butingbe.domain.zoneevent.dto.response.AdminReviewDecisionResDto(
                pid.toString(), "SUCCESS", UUID.randomUUID().toString(), 1, "SUCCESS", List.of()));
    mockMvc
        .perform(
            post("/admin/zone-event-reviews/{id}/approve", pid)
                .contentType("application/json")
                .content("{\"submissionId\":\"" + UUID.randomUUID() + "\",\"expectedRevision\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SUCCESS"));
  }

  @Test
  @DisplayName("승인 요청에 submissionId·expectedRevision이 없으면 400")
  void approveValidation() throws Exception {
    mockMvc
        .perform(
            post("/admin/zone-event-reviews/{id}/approve", UUID.randomUUID())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("반려 200 / 사유 없으면 400")
  void reject() throws Exception {
    UUID pid = UUID.randomUUID();
    mockMvc
        .perform(
            post("/admin/zone-event-reviews/{id}/reject", pid)
                .contentType("application/json")
                .content(
                    "{\"submissionId\":\""
                        + UUID.randomUUID()
                        + "\",\"reason\":\"NOT_ON_SITE\",\"expectedRevision\":0}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/admin/zone-event-reviews/{id}/reject", pid)
                .contentType("application/json")
                .content("{\"submissionId\":\"" + UUID.randomUUID() + "\",\"expectedRevision\":0}"))
        .andExpect(status().isBadRequest());
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
