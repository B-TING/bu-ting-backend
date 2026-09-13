package com.butingbe.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.dto.response.AdminMeResDto;
import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.service.AdminMeService;
import com.butingbe.global.error.GlobalExceptionHandler;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.UnauthenticatedException;
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
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

@ExtendWith(MockitoExtension.class)
class AdminMeControllerTest {

  private static final UUID USER_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");

  @Mock private AdminMeService adminMeService;
  @InjectMocks private AdminMeController controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    StaticMessageSource messageSource = new StaticMessageSource();
    messageSource.addMessage("error.operator.forbidden", Locale.KOREAN, "운영 권한이 없습니다.");
    messageSource.addMessage("error.auth.unauthenticated", Locale.KOREAN, "인증이 필요합니다.");
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .setControllerAdvice(
                new GlobalExceptionHandler(messageSource, new FixedLocaleResolver(Locale.KOREAN)))
            .build();
  }

  @Test
  @DisplayName("관리자 자기 정보 조회 200")
  void getMe() throws Exception {
    when(adminMeService.getMe(any()))
        .thenReturn(new AdminMeResDto(USER_ID.toString(), "운영자", "ADMIN", List.of("ROLE_ADMIN")));

    mockMvc
        .perform(get("/admin/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value(USER_ID.toString()))
        .andExpect(jsonPath("$.data.role").value("ADMIN"))
        .andExpect(jsonPath("$.data.permissions[0]").value("ROLE_ADMIN"));
  }

  @Test
  @DisplayName("미인증이면 401")
  void unauthenticated() throws Exception {
    when(adminMeService.getMe(any())).thenThrow(new UnauthenticatedException());
    mockMvc.perform(get("/admin/me")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("운영 권한 없으면 403")
  void forbidden() throws Exception {
    when(adminMeService.getMe(any())).thenThrow(new ForbiddenException("error.operator.forbidden"));
    mockMvc.perform(get("/admin/me")).andExpect(status().isForbidden());
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
        return new AuthenticatedUser(USER_ID, "admin@example.com", "운영자", List.of());
      }
    };
  }
}
