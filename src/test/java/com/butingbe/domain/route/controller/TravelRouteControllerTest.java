package com.butingbe.domain.route.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.route.TravelRouteService;
import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.route.dto.response.PlanRouteResDto;
import com.butingbe.domain.travel.entity.TransportType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class TravelRouteControllerTest {

  private static final UUID PLAN_ID = UUID.fromString("33333333-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  private MockMvc mockMvc;

  @Mock private TravelRouteService travelRouteService;

  @InjectMocks private TravelRouteController travelRouteController;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(travelRouteController)
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();
  }

  @Test
  @DisplayName("일정의 이동 경로를 구간과 합계로 반환한다")
  void returnsPlanRoute() throws Exception {
    RoutePoint from = RoutePoint.of("부산역", 35.1151, 129.0413);
    RoutePoint to = RoutePoint.of("광안리", 35.1532, 129.1186);
    when(travelRouteService.getPlanRoute(
            any(AuthenticatedUser.class), eq(PLAN_ID), nullable(TransportType.class)))
        .thenReturn(
            PlanRouteResDto.of(
                PLAN_ID,
                TransportType.PUBLIC_TRANSPORT,
                List.of(new RouteLeg(from, to, TransportType.PUBLIC_TRANSPORT, 11_600, 35)),
                List.of()));

    mockMvc
        .perform(get("/plans/{planId}/route", PLAN_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.planId").value(PLAN_ID.toString()))
        .andExpect(jsonPath("$.transportType").value("PUBLIC_TRANSPORT"))
        .andExpect(jsonPath("$.legs[0].from.name").value("부산역"))
        .andExpect(jsonPath("$.legs[0].to.name").value("광안리"))
        .andExpect(jsonPath("$.legs[0].durationMinutes").value(35))
        .andExpect(jsonPath("$.totalDistanceMeters").value(11600))
        .andExpect(jsonPath("$.totalDurationMinutes").value(35))
        .andExpect(jsonPath("$.skippedPlaceIds").isEmpty());
  }

  @Test
  @DisplayName("이동 수단을 지정하면 서비스에 그대로 전달한다")
  void passesTransportType() throws Exception {
    when(travelRouteService.getPlanRoute(
            any(AuthenticatedUser.class), eq(PLAN_ID), eq(TransportType.WALK)))
        .thenReturn(PlanRouteResDto.of(PLAN_ID, TransportType.WALK, List.of(), List.of()));

    mockMvc
        .perform(get("/plans/{planId}/route", PLAN_ID).param("transportType", "WALK"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transportType").value("WALK"));

    verify(travelRouteService)
        .getPlanRoute(any(AuthenticatedUser.class), eq(PLAN_ID), eq(TransportType.WALK));
  }

  @Test
  @DisplayName("알 수 없는 이동 수단은 400을 반환한다")
  void rejectsUnknownTransportType() throws Exception {
    mockMvc
        .perform(get("/plans/{planId}/route", PLAN_ID).param("transportType", "TELEPORT"))
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
        return new AuthenticatedUser(USER_ID, "u@example.com", "u", List.of());
      }
    };
  }
}
