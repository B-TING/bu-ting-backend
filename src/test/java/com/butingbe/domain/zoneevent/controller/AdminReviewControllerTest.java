package com.butingbe.domain.zoneevent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.ReviewQueuePageResDto;
import com.butingbe.domain.zoneevent.dto.response.SubmitResultResDto;
import com.butingbe.domain.zoneevent.service.AdminReviewService;
import com.butingbe.global.error.GlobalExceptionHandler;
import com.butingbe.global.error.exception.ForbiddenException;
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
class AdminReviewControllerTest {

  private static final UUID PID = UUID.fromString("33333333-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private AdminReviewService adminReviewService;
  @InjectMocks private AdminReviewController controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    StaticMessageSource messageSource = new StaticMessageSource();
    messageSource.addMessage("error.operator.forbidden", Locale.KOREAN, "운영 권한이 없습니다.");
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
  void reviewQueue() throws Exception {
    when(adminReviewService.reviewQueue(any(), any(), any()))
        .thenReturn(new ReviewQueuePageResDto(List.of(), null, false));
    mockMvc
        .perform(get("/admin/zone-event-participations/review-queue"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("승인 200 (SUCCESS 결과)")
  void approve() throws Exception {
    when(adminReviewService.approve(any(), eq(PID)))
        .thenReturn(SubmitResultResDto.of(null, List.of(), 50, List.of()));
    mockMvc
        .perform(post("/admin/zone-event-participations/{id}/approve", PID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.pointBalance").value(50));
  }

  @Test
  @DisplayName("반려 200 / 사유 없으면 400")
  void reject() throws Exception {
    mockMvc
        .perform(
            post("/admin/zone-event-participations/{id}/reject", PID)
                .contentType("application/json")
                .content("{\"failReason\":\"NOT_ON_SITE\"}"))
        .andExpect(status().isOk());
    verify(adminReviewService).reject(any(), eq(PID), eq("NOT_ON_SITE"));

    mockMvc
        .perform(
            post("/admin/zone-event-participations/{id}/reject", PID)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("회수·숨김해제 200")
  void revokeAndUnhide() throws Exception {
    mockMvc
        .perform(post("/admin/zone-event-participations/{id}/revoke", PID))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/admin/zone-event-participations/{id}/unhide", PID))
        .andExpect(status().isOk());
    verify(adminReviewService).revoke(any(), eq(PID));
    verify(adminReviewService).unhide(any(), eq(PID));
  }

  @Test
  @DisplayName("운영 권한 없으면 403")
  void forbidden() throws Exception {
    when(adminReviewService.reviewQueue(any(), any(), any()))
        .thenThrow(new ForbiddenException("error.operator.forbidden"));
    mockMvc
        .perform(get("/admin/zone-event-participations/review-queue"))
        .andExpect(status().isForbidden());
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
