package com.butingbe.domain.zoneevent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventReportDecisionResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventReportDetailResDto;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventReportPageResDto;
import com.butingbe.domain.zoneevent.service.AdminZoneEventReportService;
import com.butingbe.global.error.GlobalExceptionHandler;
import java.time.OffsetDateTime;
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
class AdminZoneEventReportControllerTest {

  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private AdminZoneEventReportService adminZoneEventReportService;
  @InjectMocks private AdminZoneEventReportController controller;

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
  @DisplayName("신고 목록 200")
  void list() throws Exception {
    when(adminZoneEventReportService.list(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new AdminZoneEventReportPageResDto(List.of(), 1, 20, 0, 0, false));
    mockMvc.perform(get("/admin/zone-event-reports")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("신고 상세 200")
  void detail() throws Exception {
    UUID reportId = UUID.randomUUID();
    when(adminZoneEventReportService.detail(any(), eq(reportId)))
        .thenReturn(
            new AdminZoneEventReportDetailResDto(
                reportId.toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                null,
                "SUYEONG_NAMGU",
                UUID.randomUUID().toString(),
                "SPAM",
                null,
                "OPEN",
                null,
                null,
                null,
                0L,
                OffsetDateTime.now(),
                List.of()));
    mockMvc.perform(get("/admin/zone-event-reports/{id}", reportId)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("신고 인정 200")
  void uphold() throws Exception {
    UUID reportId = UUID.randomUUID();
    when(adminZoneEventReportService.uphold(any(), eq(reportId), any(), any()))
        .thenReturn(
            new AdminZoneEventReportDecisionResDto(
                reportId.toString(), "UPHELD", UUID.randomUUID().toString(), 1L));
    mockMvc
        .perform(
            post("/admin/zone-event-reports/{id}/uphold", reportId)
                .contentType("application/json")
                .content("{\"note\":\"근거 확인\",\"action\":\"HOLD\",\"expectedRevision\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("UPHELD"));
  }

  @Test
  @DisplayName("신고 인정 요청에 note·action·expectedRevision이 없으면 400")
  void upholdValidation() throws Exception {
    mockMvc
        .perform(
            post("/admin/zone-event-reports/{id}/uphold", UUID.randomUUID())
                .contentType("application/json")
                .content("{}"))
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
