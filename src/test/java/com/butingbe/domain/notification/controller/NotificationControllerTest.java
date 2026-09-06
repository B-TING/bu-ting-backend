package com.butingbe.domain.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.notification.dto.response.NotificationSettingsResDto;
import com.butingbe.domain.notification.dto.response.PushLogResDto;
import com.butingbe.domain.notification.dto.response.ZoneSubscriptionsResDto;
import com.butingbe.domain.notification.service.NotificationService;
import com.butingbe.global.error.GlobalExceptionHandler;
import com.butingbe.global.error.exception.ForbiddenException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class NotificationControllerTest {

  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private NotificationService notificationService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    StaticMessageSource messageSource = new StaticMessageSource();
    messageSource.addMessage("error.operator.forbidden", Locale.KOREAN, "운영 권한이 없습니다.");
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new UserNotificationController(notificationService),
                new AdminPushController(notificationService))
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .setValidator(validator)
            .setControllerAdvice(
                new GlobalExceptionHandler(messageSource, new FixedLocaleResolver(Locale.KOREAN)))
            .build();
  }

  @Test
  @DisplayName("토큰 등록 200 / 삭제 204")
  void tokenRegisterDelete() throws Exception {
    mockMvc
        .perform(
            put("/users/me/device-tokens")
                .contentType("application/json")
                .content("{\"fcmToken\":\"t\",\"platform\":\"IOS\"}"))
        .andExpect(status().isOk());
    verify(notificationService).upsertToken(any(), eq("t"), eq("IOS"));
    mockMvc.perform(delete("/users/me/device-tokens/{t}", "tok")).andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("토큰 등록에 fcmToken 없으면 400")
  void tokenValidation() throws Exception {
    mockMvc
        .perform(
            put("/users/me/device-tokens")
                .contentType("application/json")
                .content("{\"platform\":\"IOS\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("구독 조회·설정 / 알림 설정 조회·변경")
  void subscriptionsAndSettings() throws Exception {
    when(notificationService.getSubscriptions(any()))
        .thenReturn(new ZoneSubscriptionsResDto(List.of("YEONGDO")));
    when(notificationService.setSubscriptions(any(), any()))
        .thenReturn(new ZoneSubscriptionsResDto(List.of("SUYEONG_NAMGU")));
    when(notificationService.getSettings(any()))
        .thenReturn(new NotificationSettingsResDto(Map.of("ROUND_OPEN", true)));
    when(notificationService.updateSettings(any(), any()))
        .thenReturn(new NotificationSettingsResDto(Map.of("LIKE_DEADLINE", false)));

    mockMvc
        .perform(get("/users/me/zone-subscriptions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.zoneIds[0]").value("YEONGDO"));
    mockMvc
        .perform(
            put("/users/me/zone-subscriptions")
                .contentType("application/json")
                .content("{\"zoneIds\":[\"SUYEONG_NAMGU\"]}"))
        .andExpect(status().isOk());
    mockMvc.perform(get("/users/me/notification-settings")).andExpect(status().isOk());
    mockMvc
        .perform(
            patch("/users/me/notification-settings")
                .contentType("application/json")
                .content("{\"LIKE_DEADLINE\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.settings.LIKE_DEADLINE").value(false));
  }

  @Test
  @DisplayName("운영 푸시 201 / 권한 없으면 403")
  void adminPush() throws Exception {
    when(notificationService.operatorPush(any(), eq("ALL"), any(), eq("공지"), eq("본문")))
        .thenReturn(new PushLogResDto(UUID.randomUUID().toString(), 5));
    mockMvc
        .perform(
            post("/admin/push")
                .contentType("application/json")
                .content("{\"targetType\":\"ALL\",\"title\":\"공지\",\"body\":\"본문\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.recipientCount").value(5));

    when(notificationService.operatorPush(any(), any(), any(), any(), any()))
        .thenThrow(new ForbiddenException("error.operator.forbidden"));
    mockMvc
        .perform(
            post("/admin/push")
                .contentType("application/json")
                .content("{\"targetType\":\"ALL\",\"title\":\"t\",\"body\":\"b\"}"))
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
        return new AuthenticatedUser(USER_ID, "u@example.com", "u", List.of());
      }
    };
  }
}
