package com.butingbe.domain.reward.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.dto.response.AdminRewardGrantPageResDto;
import com.butingbe.domain.reward.dto.response.RewardCatalogResDto;
import com.butingbe.domain.reward.service.AdminRewardCatalogService;
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
class AdminRewardCatalogControllerTest {

  private static final UUID REWARD_ID = UUID.fromString("44444444-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private AdminRewardCatalogService adminRewardCatalogService;
  @InjectMocks private AdminRewardCatalogController controller;

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
  @DisplayName("카탈로그 생성은 201을 반환한다")
  void create() throws Exception {
    when(adminRewardCatalogService.create(any(), any())).thenReturn(catalog());

    mockMvc
        .perform(
            post("/admin/reward-catalog")
                .contentType("application/json")
                .content("{\"rewardType\":\"COUPON\",\"code\":\"COUPON_A\",\"name\":\"쿠폰\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.code").value("COUPON_A"));
  }

  @Test
  @DisplayName("code가 없으면 400(검증 실패)이다")
  void createMissingCode() throws Exception {
    mockMvc
        .perform(
            post("/admin/reward-catalog")
                .contentType("application/json")
                .content("{\"rewardType\":\"COUPON\",\"name\":\"쿠폰\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("목록·수정·지급이력은 200을 반환한다")
  void listUpdateGrants() throws Exception {
    when(adminRewardCatalogService.list(any(), any(), any())).thenReturn(List.of(catalog()));
    when(adminRewardCatalogService.update(any(), eq(REWARD_ID), any())).thenReturn(catalog());
    when(adminRewardCatalogService.grants(any(), eq(REWARD_ID), any(), any()))
        .thenReturn(new AdminRewardGrantPageResDto(List.of(), null, false));

    mockMvc.perform(get("/admin/reward-catalog")).andExpect(status().isOk());
    mockMvc
        .perform(
            patch("/admin/reward-catalog/{rewardId}", REWARD_ID)
                .contentType("application/json")
                .content("{\"stock\":5}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.code").value("COUPON_A"));
    mockMvc
        .perform(get("/admin/reward-catalog/{rewardId}/grants", REWARD_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.hasNext").value(false));
  }

  @Test
  @DisplayName("운영 권한이 없으면 403이다")
  void forbidden() throws Exception {
    when(adminRewardCatalogService.list(any(), any(), any()))
        .thenThrow(new ForbiddenException("error.operator.forbidden"));

    mockMvc
        .perform(get("/admin/reward-catalog"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("운영 권한이 없습니다."));
  }

  private RewardCatalogResDto catalog() {
    return new RewardCatalogResDto(
        REWARD_ID.toString(), "COUPON", "COUPON_A", "쿠폰", null, null, 100, 10, 30, true);
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
        return new AuthenticatedUser(USER_ID, "admin@example.com", "admin", List.of());
      }
    };
  }
}
