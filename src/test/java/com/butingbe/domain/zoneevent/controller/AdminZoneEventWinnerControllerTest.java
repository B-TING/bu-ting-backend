package com.butingbe.domain.zoneevent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.WinnerConfirmResDto;
import com.butingbe.domain.zoneevent.service.AdminZoneEventWinnerService;
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
class AdminZoneEventWinnerControllerTest {

  private static final UUID EVENT_ID = UUID.fromString("44444444-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private AdminZoneEventWinnerService winnerService;
  @InjectMocks private AdminZoneEventWinnerController controller;

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
  @DisplayName("수상자 확정 200")
  void confirmWinners() throws Exception {
    when(winnerService.confirmWinners(any(), eq(EVENT_ID), any(), isNull()))
        .thenReturn(new WinnerConfirmResDto(EVENT_ID.toString(), 1, List.of()));

    mockMvc
        .perform(
            post("/admin/zone-events/{eventId}/winners/confirm", EVENT_ID)
                .contentType("application/json")
                .content(
                    "{\"snapshotId\":\"11111111-0000-0000-0000-000000000001\",\"participationIds\":[\"22222222-0000-0000-0000-000000000002\"],\"selectionReason\":\"사유\",\"expectedRevision\":1}"))
        .andExpect(status().isOk());
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
