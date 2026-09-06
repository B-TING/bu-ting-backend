package com.butingbe.domain.zoneevent.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.AuthTargetBriefResDto;
import com.butingbe.domain.zoneevent.dto.response.RewardSummaryResDto;
import com.butingbe.domain.zoneevent.dto.response.ZoneEventSummaryResDto;
import com.butingbe.domain.zoneevent.dto.response.ZoneRef;
import com.butingbe.domain.zoneevent.service.ZoneEventQueryService;
import com.butingbe.global.error.GlobalExceptionHandler;
import com.butingbe.global.error.exception.ResourceNotFoundException;
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
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

@ExtendWith(MockitoExtension.class)
class ZoneEventControllerTest {

  private static final UUID EVENT_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private ZoneEventQueryService zoneEventQueryService;
  @InjectMocks private ZoneEventController controller;

  private MockMvc mockMvc;
  private AuthenticatedUser currentUser;

  @BeforeEach
  void setUp() {
    StaticMessageSource messageSource = new StaticMessageSource();
    messageSource.addMessage("error.zone_event.invalid_zone", Locale.KOREAN, "유효하지 않은 구역입니다.");
    messageSource.addMessage("error.zone_event.not_found", Locale.KOREAN, "이벤트를 찾을 수 없습니다.");
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .setControllerAdvice(
                new GlobalExceptionHandler(messageSource, new FixedLocaleResolver(Locale.KOREAN)))
            .build();
  }

  @Test
  @DisplayName("활성 이벤트 목록을 ApiResponse로 감싸 반환한다")
  void getActiveEvents() throws Exception {
    currentUser = null;
    when(zoneEventQueryService.getActiveEvents(eq("SUYEONG_NAMGU"), isNull()))
        .thenReturn(List.of(summary()));

    mockMvc
        .perform(get("/zone-events/active").param("zone", "SUYEONG_NAMGU"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].eventId").value(EVENT_ID.toString()))
        .andExpect(jsonPath("$.data[0].zone.zoneId").value("SUYEONG_NAMGU"))
        .andExpect(jsonPath("$.data[0].baseReward.points").value(50))
        .andExpect(jsonPath("$.data[0].myParticipationStatus").doesNotExist());
  }

  @Test
  @DisplayName("로그인 유저의 요청은 userId를 서비스에 전달한다")
  void passesUserIdWhenLoggedIn() throws Exception {
    currentUser = new AuthenticatedUser(USER_ID, "u@example.com", "u", List.of());
    when(zoneEventQueryService.getActiveEvents(eq("YEONGDO"), eq(USER_ID))).thenReturn(List.of());

    mockMvc.perform(get("/zone-events/active").param("zone", "YEONGDO")).andExpect(status().isOk());

    verify(zoneEventQueryService).getActiveEvents("YEONGDO", USER_ID);
  }

  @Test
  @DisplayName("잘못된 구역은 400을 반환한다")
  void invalidZoneReturns400() throws Exception {
    currentUser = null;
    when(zoneEventQueryService.getActiveEvents(eq("NOWHERE"), isNull()))
        .thenThrow(new IllegalArgumentException("error.zone_event.invalid_zone"));

    mockMvc
        .perform(get("/zone-events/active").param("zone", "NOWHERE"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("유효하지 않은 구역입니다."));
  }

  @Test
  @DisplayName("이벤트 상세를 ApiResponse로 감싸 반환한다")
  void getEventDetail() throws Exception {
    currentUser = new AuthenticatedUser(USER_ID, "u@example.com", "u", List.of());
    when(zoneEventQueryService.getEventDetail(eq(EVENT_ID), eq(USER_ID)))
        .thenReturn(
            new com.butingbe.domain.zoneevent.dto.response.ZoneEventDetailResDto(
                EVENT_ID.toString(),
                ZoneRef.from("SUYEONG_NAMGU"),
                "PLACE_AUTH",
                "장소 인증",
                true,
                "광안대교 야경 담기",
                "야경 촬영",
                OffsetDateTime.now().minusHours(1),
                OffsetDateTime.now().plusHours(23),
                1440,
                52340,
                "ACTIVE",
                null,
                new RewardSummaryResDto(50, "SPOT_GWANGAN_BRIDGE", null, null),
                new RewardSummaryResDto(null, null, 5, "COUPON_CAFE_3000"),
                new com.butingbe.domain.zoneevent.dto.response.AuthTargetDetailResDto(
                    UUID.randomUUID().toString(),
                    "PLACE",
                    "gwangan-bridge",
                    "광안대교 야경",
                    "가로로 촬영",
                    "https://signed.example/example.jpg",
                    35.153,
                    129.118,
                    100),
                27,
                1,
                1,
                null));

    mockMvc
        .perform(get("/zone-events/{eventId}", EVENT_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.eventId").value(EVENT_ID.toString()))
        .andExpect(jsonPath("$.data.successLimitPerUser").value(1))
        .andExpect(
            jsonPath("$.data.authTarget.exampleImageUrl")
                .value("https://signed.example/example.jpg"))
        .andExpect(jsonPath("$.data.excellenceReward.topN").value(5));
  }

  @Test
  @DisplayName("없는 이벤트 상세는 404를 반환한다")
  void detailNotFoundReturns404() throws Exception {
    currentUser = null;
    when(zoneEventQueryService.getEventDetail(eq(EVENT_ID), isNull()))
        .thenThrow(new ResourceNotFoundException("error.zone_event.not_found"));

    mockMvc
        .perform(get("/zone-events/{eventId}", EVENT_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("이벤트를 찾을 수 없습니다."));
  }

  private ZoneEventSummaryResDto summary() {
    return new ZoneEventSummaryResDto(
        EVENT_ID.toString(),
        ZoneRef.from("SUYEONG_NAMGU"),
        "PLACE_AUTH",
        "장소 인증",
        true,
        "광안대교 야경 담기",
        "야경 촬영",
        OffsetDateTime.now().minusHours(1),
        OffsetDateTime.now().plusHours(23),
        1440,
        52340,
        "ACTIVE",
        null,
        new RewardSummaryResDto(50, "SPOT_GWANGAN_BRIDGE", null, null),
        new AuthTargetBriefResDto("광안대교 야경", 35.153, 129.118, 100),
        27,
        null,
        null);
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
        return currentUser;
      }
    };
  }
}
