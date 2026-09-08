package com.butingbe.domain.zoneevent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.AdminAuthTargetResDto;
import com.butingbe.domain.zoneevent.service.AdminZoneEventTargetService;
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
class AdminZoneEventTargetControllerTest {

  private static final UUID EVENT_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID TARGET_ID = UUID.fromString("33333333-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private AdminZoneEventTargetService targetService;
  @InjectMocks private AdminZoneEventTargetController controller;

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
  @DisplayName("타겟 생성은 201을 반환한다")
  void create() throws Exception {
    when(targetService.create(any(), eq(EVENT_ID), any())).thenReturn(target("ACTIVE"));

    mockMvc
        .perform(
            post("/admin/zone-events/{eventId}/targets", EVENT_ID)
                .contentType("application/json")
                .content(
                    """
                    {
                      "targetKind":"PLACE","placeContentId":"126081","contentTypeId":"12",
                      "latitude":35.1532,"longitude":129.1181,"radiusM":80
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));
  }

  @Test
  @DisplayName("반경이 범위를 벗어나면 400이다")
  void createInvalidRadius() throws Exception {
    mockMvc
        .perform(
            post("/admin/zone-events/{eventId}/targets", EVENT_ID)
                .contentType("application/json")
                .content(
                    """
                    {
                      "targetKind":"PLACE","placeContentId":"126081","contentTypeId":"12",
                      "latitude":35.1532,"longitude":129.1181,"radiusM":10
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("목록은 200을 반환한다")
  void list() throws Exception {
    when(targetService.list(any(), eq(EVENT_ID))).thenReturn(List.of(target("ACTIVE")));

    mockMvc
        .perform(get("/admin/zone-events/{eventId}/targets", EVENT_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
  }

  @Test
  @DisplayName("수정은 200을 반환한다")
  void patchTarget() throws Exception {
    when(targetService.patch(any(), eq(EVENT_ID), eq(TARGET_ID), any()))
        .thenReturn(target("ACTIVE"));

    mockMvc
        .perform(
            patch("/admin/zone-events/{eventId}/targets/{targetId}", EVENT_ID, TARGET_ID)
                .contentType("application/json")
                .content("{\"radiusM\":200,\"expectedRevision\":0}"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("expectedRevision이 빠지면 400이다")
  void patchWithoutExpectedRevisionIsBadRequest() throws Exception {
    mockMvc
        .perform(
            patch("/admin/zone-events/{eventId}/targets/{targetId}", EVENT_ID, TARGET_ID)
                .contentType("application/json")
                .content("{\"radiusM\":200}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("교체·취소는 200을 반환한다")
  void replaceAndCancel() throws Exception {
    when(targetService.replace(any(), eq(EVENT_ID), eq(TARGET_ID), any()))
        .thenReturn(target("ACTIVE"));
    when(targetService.cancel(any(), eq(EVENT_ID), eq(TARGET_ID))).thenReturn(target("CANCELLED"));

    mockMvc
        .perform(
            post("/admin/zone-events/{eventId}/targets/{targetId}/replace", EVENT_ID, TARGET_ID)
                .contentType("application/json")
                .content(
                    "{\"placeContentId\":\"999999\",\"contentTypeId\":\"12\",\"radiusM\":100}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(post("/admin/zone-events/{eventId}/targets/{targetId}/cancel", EVENT_ID, TARGET_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CANCELLED"));
  }

  @Test
  @DisplayName("운영 권한이 없으면 403이다")
  void forbidden() throws Exception {
    when(targetService.list(any(), eq(EVENT_ID)))
        .thenThrow(new ForbiddenException("error.operator.forbidden"));

    mockMvc
        .perform(get("/admin/zone-events/{eventId}/targets", EVENT_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("운영 권한이 없습니다."));
  }

  private AdminAuthTargetResDto target(String status) {
    return new AdminAuthTargetResDto(
        TARGET_ID.toString(),
        EVENT_ID.toString(),
        "PLACE",
        null,
        "126081",
        "12",
        "광안대교",
        "가이드",
        null,
        35.1532,
        129.1181,
        35.1532,
        129.1181,
        false,
        80,
        status,
        0L);
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
