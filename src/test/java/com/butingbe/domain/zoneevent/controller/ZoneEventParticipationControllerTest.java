package com.butingbe.domain.zoneevent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.zoneevent.dto.response.ParticipationResDto;
import com.butingbe.domain.zoneevent.exception.OpenParticipationExistsException;
import com.butingbe.domain.zoneevent.exception.ZoneEventOutOfRangeException;
import com.butingbe.domain.zoneevent.service.ZoneEventParticipationService;
import com.butingbe.global.error.GlobalExceptionHandler;
import com.butingbe.global.error.exception.UnauthenticatedException;
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
class ZoneEventParticipationControllerTest {

  private static final UUID EVENT_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");
  private static final UUID OPEN_ID = UUID.fromString("33333333-0000-0000-0000-000000000001");

  @Mock private ZoneEventParticipationService participationService;
  @InjectMocks private ZoneEventParticipationController controller;

  private MockMvc mockMvc;
  private AuthenticatedUser currentUser;

  @BeforeEach
  void setUp() {
    currentUser = new AuthenticatedUser(USER_ID, "u@example.com", "u", List.of());
    StaticMessageSource messageSource = new StaticMessageSource();
    messageSource.addMessage("error.zone_event.out_of_range", Locale.KOREAN, "반경 밖입니다.");
    messageSource.addMessage(
        "error.zone_event.participation.already_open", Locale.KOREAN, "이미 진행 중인 참여가 있습니다.");
    messageSource.addMessage("error.auth.unauthenticated", Locale.KOREAN, "인증이 필요합니다.");
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
  @DisplayName("반경 이내 참여 시작은 201과 JOINED 참여를 반환한다")
  void joinReturns201() throws Exception {
    when(participationService.join(any(), eq(EVENT_ID), anyDouble(), anyDouble()))
        .thenReturn(
            new ParticipationResDto(
                OPEN_ID.toString(),
                EVENT_ID.toString(),
                "SUYEONG_NAMGU",
                "PLACE_AUTH",
                "JOINED",
                null,
                28,
                null,
                null,
                0,
                "PUBLIC",
                OffsetDateTime.now(),
                null,
                List.of()));

    mockMvc
        .perform(
            post("/zone-events/{eventId}/participations", EVENT_ID)
                .contentType("application/json")
                .content("{\"latitude\":35.1532,\"longitude\":129.1182}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("JOINED"))
        .andExpect(jsonPath("$.data.distanceM").value(28));
  }

  @Test
  @DisplayName("반경 밖이면 400과 data에 거리를 담는다")
  void outOfRangeReturns400WithDistance() throws Exception {
    when(participationService.join(any(), eq(EVENT_ID), anyDouble(), anyDouble()))
        .thenThrow(new ZoneEventOutOfRangeException(1340));

    mockMvc
        .perform(
            post("/zone-events/{eventId}/participations", EVENT_ID)
                .contentType("application/json")
                .content("{\"latitude\":35.16,\"longitude\":129.13}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("반경 밖입니다."))
        .andExpect(jsonPath("$.data.distanceMeters").value(1340));
  }

  @Test
  @DisplayName("이미 열린 참여가 있으면 409와 data에 참여 id를 담는다")
  void alreadyOpenReturns409WithId() throws Exception {
    when(participationService.join(any(), eq(EVENT_ID), anyDouble(), anyDouble()))
        .thenThrow(new OpenParticipationExistsException(OPEN_ID));

    mockMvc
        .perform(
            post("/zone-events/{eventId}/participations", EVENT_ID)
                .contentType("application/json")
                .content("{\"latitude\":35.1532,\"longitude\":129.1182}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.data.participationId").value(OPEN_ID.toString()));
  }

  @Test
  @DisplayName("열린 참여 id를 못 찾은 동시성 충돌은 409에 빈 data다")
  void alreadyOpenWithoutIdReturns409EmptyData() throws Exception {
    when(participationService.join(any(), eq(EVENT_ID), anyDouble(), anyDouble()))
        .thenThrow(new OpenParticipationExistsException(null));

    mockMvc
        .perform(
            post("/zone-events/{eventId}/participations", EVENT_ID)
                .contentType("application/json")
                .content("{\"latitude\":35.1532,\"longitude\":129.1182}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.data.participationId").doesNotExist());
  }

  @Test
  @DisplayName("미인증이면 401이다")
  void unauthenticatedReturns401() throws Exception {
    when(participationService.join(any(), eq(EVENT_ID), anyDouble(), anyDouble()))
        .thenThrow(new UnauthenticatedException());

    mockMvc
        .perform(
            post("/zone-events/{eventId}/participations", EVENT_ID)
                .contentType("application/json")
                .content("{\"latitude\":35.1532,\"longitude\":129.1182}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("좌표가 없으면 400(검증 실패)이다")
  void missingCoordinatesReturns400() throws Exception {
    mockMvc
        .perform(
            post("/zone-events/{eventId}/participations", EVENT_ID)
                .contentType("application/json")
                .content("{\"latitude\":35.1532}"))
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
        return currentUser;
      }
    };
  }
}
