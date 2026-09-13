package com.butingbe.domain.route.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.route.TravelRouteService;
import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.route.dto.response.TravelRouteOptimizeResDto;
import com.butingbe.domain.route.dto.response.VisitOrderResDto;
import com.butingbe.domain.travel.entity.TransportType;
import java.time.LocalDate;
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
class TravelRouteOptimizeControllerTest {

  private static final UUID TRAVEL_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID PLAN_ID = UUID.fromString("33333333-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  private MockMvc mockMvc;

  @Mock private TravelRouteService travelRouteService;

  @InjectMocks private TravelRouteOptimizeController travelRouteOptimizeController;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(travelRouteOptimizeController)
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();
  }

  @Test
  @DisplayName("여행 전체의 일자별 최적화 결과를 반환한다")
  void returnsPerDayOptimization() throws Exception {
    RoutePoint first = RoutePoint.of("부산역", 35.1151, 129.0413);
    RoutePoint second = RoutePoint.of("광안리", 35.1532, 129.1186);
    VisitOrderResDto dayRoute =
        VisitOrderResDto.of(
            TransportType.PUBLIC_TRANSPORT,
            List.of(first, second),
            List.of(new RouteLeg(first, second, TransportType.PUBLIC_TRANSPORT, 10_800, 53)),
            70,
            List.of());
    when(travelRouteService.optimizeTravelVisitOrder(
            any(AuthenticatedUser.class), eq(TRAVEL_ID), nullable(TransportType.class)))
        .thenReturn(
            TravelRouteOptimizeResDto.of(
                TRAVEL_ID,
                TransportType.PUBLIC_TRANSPORT,
                List.of(
                    TravelRouteOptimizeResDto.DayRoute.of(
                        PLAN_ID, 1, LocalDate.of(2026, 9, 1), dayRoute))));

    mockMvc
        .perform(post("/travels/{travelId}/route/optimize", TRAVEL_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.travelId").value(TRAVEL_ID.toString()))
        .andExpect(jsonPath("$.days[0].planId").value(PLAN_ID.toString()))
        .andExpect(jsonPath("$.days[0].dayNumber").value(1))
        .andExpect(jsonPath("$.days[0].visitDate").value("2026-09-01"))
        .andExpect(jsonPath("$.days[0].route.orderedPoints[0].name").value("부산역"))
        .andExpect(jsonPath("$.days[0].savedMinutes").value(17))
        .andExpect(jsonPath("$.totalDurationMinutes").value(53))
        .andExpect(jsonPath("$.originalDurationMinutes").value(70))
        .andExpect(jsonPath("$.savedMinutes").value(17));
  }

  @Test
  @DisplayName("이동 수단을 지정하면 서비스에 그대로 전달한다")
  void passesTransportType() throws Exception {
    when(travelRouteService.optimizeTravelVisitOrder(
            any(AuthenticatedUser.class), eq(TRAVEL_ID), eq(TransportType.CAR)))
        .thenReturn(TravelRouteOptimizeResDto.of(TRAVEL_ID, TransportType.CAR, List.of()));

    mockMvc
        .perform(
            post("/travels/{travelId}/route/optimize", TRAVEL_ID).param("transportType", "CAR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transportType").value("CAR"))
        .andExpect(jsonPath("$.days").isEmpty());

    verify(travelRouteService)
        .optimizeTravelVisitOrder(
            any(AuthenticatedUser.class), eq(TRAVEL_ID), eq(TransportType.CAR));
  }

  @Test
  @DisplayName("알 수 없는 이동 수단은 400을 반환한다")
  void rejectsUnknownTransportType() throws Exception {
    mockMvc
        .perform(
            post("/travels/{travelId}/route/optimize", TRAVEL_ID)
                .param("transportType", "TELEPORT"))
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
