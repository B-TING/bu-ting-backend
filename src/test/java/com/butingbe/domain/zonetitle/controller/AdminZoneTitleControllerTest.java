package com.butingbe.domain.zonetitle.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zonetitle.dto.response.AdminZoneTitleDefResDto;
import com.butingbe.domain.zonetitle.service.AdminZoneTitleService;
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
class AdminZoneTitleControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @Mock private AdminZoneTitleService adminZoneTitleService;
  @InjectMocks private AdminZoneTitleController controller;

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
  @DisplayName("칭호 정의 목록 200")
  void list() throws Exception {
    when(adminZoneTitleService.list(any())).thenReturn(List.of());

    mockMvc.perform(get("/admin/zone-titles")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("칭호 정의 생성 200")
  void create() throws Exception {
    when(adminZoneTitleService.create(any(), any()))
        .thenReturn(
            new AdminZoneTitleDefResDto(
                UUID.randomUUID().toString(),
                "SUYEONG_NAMGU_T1",
                "SUYEONG_NAMGU",
                1,
                1,
                "탐방가",
                "chip",
                "#000000",
                0L,
                0L,
                null,
                null));

    mockMvc
        .perform(
            post("/admin/zone-titles")
                .contentType("application/json")
                .content(
                    "{\"zoneId\":\"SUYEONG_NAMGU\",\"tier\":1,\"requiredSuccessCount\":1,"
                        + "\"titleName\":\"탐방가\",\"style\":\"chip\",\"color\":\"#000000\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.titleCode").value("SUYEONG_NAMGU_T1"));
  }

  @Test
  @DisplayName("필수값 누락이면 400")
  void createValidation() throws Exception {
    mockMvc
        .perform(post("/admin/zone-titles").contentType("application/json").content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("칭호 정의 수정 200")
  void update() throws Exception {
    UUID titleDefId = UUID.randomUUID();
    when(adminZoneTitleService.update(any(), eq(titleDefId), any()))
        .thenReturn(
            new AdminZoneTitleDefResDto(
                titleDefId.toString(),
                "SUYEONG_NAMGU_T1",
                "SUYEONG_NAMGU",
                1,
                3,
                "탐방가",
                "chip",
                "#000000",
                2L,
                1L,
                null,
                null));

    mockMvc
        .perform(
            patch("/admin/zone-titles/{id}", titleDefId)
                .contentType("application/json")
                .content(
                    "{\"requiredSuccessCount\":3,\"retroactive\":true,\"expectedRevision\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.requiredSuccessCount").value(3));
  }

  @Test
  @DisplayName("칭호 정의 삭제 204")
  void deleteReturnsNoContent() throws Exception {
    UUID titleDefId = UUID.randomUUID();

    mockMvc
        .perform(delete("/admin/zone-titles/{id}", titleDefId))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("칭호 보유자 목록 200")
  void holders() throws Exception {
    UUID titleDefId = UUID.randomUUID();
    when(adminZoneTitleService.holders(any(), eq(titleDefId), any(), any()))
        .thenReturn(
            new com.butingbe.domain.zonetitle.dto.response.AdminZoneTitleHolderPageResDto(
                List.of(), 1, 20, 0, 1, false));

    mockMvc
        .perform(get("/admin/zone-titles/{id}/holders", titleDefId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray());
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
