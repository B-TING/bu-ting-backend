package com.butingbe.domain.zoneevent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.AdminZoneEventResDto;
import com.butingbe.domain.zoneevent.service.AdminZoneEventService;
import com.butingbe.global.error.GlobalExceptionHandler;
import com.butingbe.global.error.exception.ForbiddenException;
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
class AdminZoneEventControllerTest {

  private static final UUID EVENT_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private AdminZoneEventService adminZoneEventService;
  @InjectMocks private AdminZoneEventController controller;

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
  @DisplayName("이벤트 생성은 201을 반환한다")
  void create() throws Exception {
    when(adminZoneEventService.create(any(), any())).thenReturn(detail("SCHEDULED"));

    mockMvc
        .perform(
            post("/admin/zone-events")
                .contentType("application/json")
                .content(
                    """
                    {
                      "zoneId":"SUYEONG_NAMGU","typeCode":"PLACE_AUTH","title":"제목",
                      "startsAt":"2026-09-06T10:00:00+09:00","durationMinutes":1440,
                      "baseReward":{"points":50,"badgeCode":"SPOT"},
                      "authTarget":{"targetKind":"PLACE","placeName":"광안","latitude":35.1,"longitude":129.1,"radiusM":100}
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
  }

  @Test
  @DisplayName("반경이 범위를 벗어나면 400(검증 실패)이다")
  void createInvalidRadius() throws Exception {
    mockMvc
        .perform(
            post("/admin/zone-events")
                .contentType("application/json")
                .content(
                    """
                    {
                      "zoneId":"SUYEONG_NAMGU","typeCode":"PLACE_AUTH","title":"제목",
                      "startsAt":"2026-09-06T10:00:00+09:00","durationMinutes":1440,
                      "baseReward":{"points":50},
                      "authTarget":{"targetKind":"PLACE","placeName":"광안","latitude":35.1,"longitude":129.1,"radiusM":10}
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("운영 권한이 없으면 403이다")
  void forbidden() throws Exception {
    when(adminZoneEventService.detail(any(), eq(EVENT_ID)))
        .thenThrow(new ForbiddenException("error.operator.forbidden"));

    mockMvc
        .perform(get("/admin/zone-events/{eventId}", EVENT_ID))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("운영 권한이 없습니다."));
  }

  @Test
  @DisplayName("상세는 200을 반환한다")
  void detailOk() throws Exception {
    when(adminZoneEventService.detail(any(), eq(EVENT_ID))).thenReturn(detail("ACTIVE"));

    mockMvc
        .perform(get("/admin/zone-events/{eventId}", EVENT_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.eventId").value(EVENT_ID.toString()));
  }

  @Test
  @DisplayName("상태 전환은 200을 반환한다")
  void activate() throws Exception {
    when(adminZoneEventService.activate(any(), eq(EVENT_ID))).thenReturn(detail("ACTIVE"));

    mockMvc
        .perform(post("/admin/zone-events/{eventId}/activate", EVENT_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));
  }

  @Test
  @DisplayName("목록은 200을 반환한다")
  void list() throws Exception {
    when(adminZoneEventService.list(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(
            new com.butingbe.domain.zoneevent.dto.response.AdminZoneEventPageResDto(
                List.of(detail("SCHEDULED")), null, false));

    mockMvc
        .perform(get("/admin/zone-events").param("zone", "SUYEONG_NAMGU"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].eventId").value(EVENT_ID.toString()));
  }

  @Test
  @DisplayName("수정·종료·취소는 200을 반환한다")
  void updateCloseCancel() throws Exception {
    when(adminZoneEventService.update(any(), eq(EVENT_ID), any())).thenReturn(detail("ACTIVE"));
    when(adminZoneEventService.close(any(), eq(EVENT_ID))).thenReturn(detail("CLOSED"));
    when(adminZoneEventService.cancel(any(), eq(EVENT_ID))).thenReturn(detail("CANCELLED"));

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/admin/zone-events/{eventId}", EVENT_ID)
                .contentType("application/json")
                .content("{\"title\":\"새 제목\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/admin/zone-events/{eventId}/close", EVENT_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CLOSED"));
    mockMvc
        .perform(post("/admin/zone-events/{eventId}/cancel", EVENT_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CANCELLED"));
  }

  private AdminZoneEventResDto detail(String status) {
    return new AdminZoneEventResDto(
        EVENT_ID.toString(),
        "SUYEONG_NAMGU",
        "PLACE_AUTH",
        "제목",
        null,
        OffsetDateTime.now(),
        OffsetDateTime.now().plusDays(1),
        1440,
        status,
        null,
        1,
        null,
        null,
        null,
        0,
        0);
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
