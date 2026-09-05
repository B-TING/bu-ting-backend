package com.butingbe.domain.route.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.route.TravelRouteService;
import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.route.dto.response.AlternativeRouteResDto;
import com.butingbe.domain.route.dto.response.PlanRouteResDto;
import com.butingbe.domain.route.dto.response.VisitOrderResDto;
import com.butingbe.domain.travel.dto.response.PlanPlaceResDto;
import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travel.entity.TransportType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
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
            .setValidator(validator())
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

  @Test
  @DisplayName("방문 순서 최적화 결과를 절약 시간과 함께 반환한다")
  void returnsOptimizedVisitOrder() throws Exception {
    RoutePoint first = RoutePoint.of("부산역", 35.1151, 129.0413);
    RoutePoint second = RoutePoint.of("광안리", 35.1532, 129.1186);
    when(travelRouteService.optimizeVisitOrder(
            any(AuthenticatedUser.class),
            eq(PLAN_ID),
            nullable(RoutePoint.class),
            nullable(TransportType.class)))
        .thenReturn(
            VisitOrderResDto.of(
                TransportType.PUBLIC_TRANSPORT,
                List.of(first, second),
                List.of(new RouteLeg(first, second, TransportType.PUBLIC_TRANSPORT, 10_800, 53)),
                70,
                List.of()));

    mockMvc
        .perform(
            post("/plans/{planId}/route/optimize", PLAN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"transportType": "PUBLIC_TRANSPORT"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderedPoints[0].name").value("부산역"))
        .andExpect(jsonPath("$.orderedPoints[1].name").value("광안리"))
        .andExpect(jsonPath("$.totalDurationMinutes").value(53))
        .andExpect(jsonPath("$.originalDurationMinutes").value(70))
        .andExpect(jsonPath("$.savedMinutes").value(17));
  }

  @Test
  @DisplayName("출발 좌표를 주면 출발 지점으로 서비스에 전달한다")
  void passesStartingPoint() throws Exception {
    when(travelRouteService.optimizeVisitOrder(
            any(AuthenticatedUser.class),
            eq(PLAN_ID),
            nullable(RoutePoint.class),
            nullable(TransportType.class)))
        .thenReturn(VisitOrderResDto.of(TransportType.WALK, List.of(), List.of(), 0, List.of()));

    mockMvc
        .perform(
            post("/plans/{planId}/route/optimize", PLAN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"startLatitude": 35.1587, "startLongitude": 129.1604,
                     "startName": "현재 위치", "transportType": "WALK"}
                    """))
        .andExpect(status().isOk());

    ArgumentCaptor<RoutePoint> captor = ArgumentCaptor.forClass(RoutePoint.class);
    verify(travelRouteService)
        .optimizeVisitOrder(
            any(AuthenticatedUser.class), eq(PLAN_ID), captor.capture(), eq(TransportType.WALK));
    assertThat(captor.getValue().name()).isEqualTo("현재 위치");
    assertThat(captor.getValue().latitude()).isEqualTo(35.1587);
  }

  @Test
  @DisplayName("본문 없이 요청해도 기본값으로 최적화한다")
  void optimizesWithoutABody() throws Exception {
    when(travelRouteService.optimizeVisitOrder(
            any(AuthenticatedUser.class),
            eq(PLAN_ID),
            nullable(RoutePoint.class),
            nullable(TransportType.class)))
        .thenReturn(
            VisitOrderResDto.of(
                TransportType.PUBLIC_TRANSPORT, List.of(), List.of(), 0, List.of()));

    mockMvc.perform(post("/plans/{planId}/route/optimize", PLAN_ID)).andExpect(status().isOk());

    verify(travelRouteService)
        .optimizeVisitOrder(
            any(AuthenticatedUser.class), eq(PLAN_ID), nullable(RoutePoint.class), eq(null));
  }

  @Test
  @DisplayName("출발 좌표가 범위를 벗어나면 400을 반환한다")
  void rejectsOutOfRangeStartCoordinates() throws Exception {
    mockMvc
        .perform(
            post("/plans/{planId}/route/optimize", PLAN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"startLatitude": 95.0, "startLongitude": 129.1604}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("최적화한 순서를 반영하면 갱신된 장소 목록을 반환한다")
  void appliesOptimizedOrder() throws Exception {
    UUID firstId = UUID.fromString("55555555-0000-0000-0000-000000000001");
    UUID secondId = UUID.fromString("55555555-0000-0000-0000-000000000002");
    when(travelRouteService.applyOptimizedOrder(
            any(AuthenticatedUser.class), eq(PLAN_ID), eq(List.of(secondId, firstId))))
        .thenReturn(List.of(planPlace(secondId, 1, "부산역"), planPlace(firstId, 2, "해운대")));

    mockMvc
        .perform(
            post("/plans/{planId}/route/apply", PLAN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"planPlaceIds": ["%s", "%s"]}
                    """
                        .formatted(secondId, firstId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].placeName").value("부산역"))
        .andExpect(jsonPath("$[0].sequence").value(1))
        .andExpect(jsonPath("$[1].placeName").value("해운대"))
        .andExpect(jsonPath("$[1].sequence").value(2));
  }

  @Test
  @DisplayName("반영할 장소 목록이 비어 있으면 400을 반환한다")
  void rejectsEmptyPlaceIds() throws Exception {
    mockMvc
        .perform(
            post("/plans/{planId}/route/apply", PLAN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"planPlaceIds": []}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("제외할 장소를 지정하면 대체 경로를 반환한다")
  void returnsAlternativeRoute() throws Exception {
    UUID excludedId = UUID.fromString("55555555-0000-0000-0000-000000000009");
    RoutePoint nampo = RoutePoint.of("남포동", 35.0979, 129.0301);
    RoutePoint gwangalli = RoutePoint.of("광안리", 35.1532, 129.1186);
    VisitOrderResDto alternative =
        VisitOrderResDto.of(
            TransportType.PUBLIC_TRANSPORT,
            List.of(nampo, gwangalli),
            List.of(new RouteLeg(nampo, gwangalli, TransportType.PUBLIC_TRANSPORT, 12_000, 40)),
            40,
            List.of());
    when(travelRouteService.generateAlternativeRoute(
            any(AuthenticatedUser.class),
            eq(PLAN_ID),
            eq(List.of(excludedId)),
            nullable(RoutePoint.class),
            nullable(TransportType.class)))
        .thenReturn(
            AlternativeRouteResDto.of(
                TransportType.PUBLIC_TRANSPORT, alternative, 95, List.of(excludedId), List.of()));

    mockMvc
        .perform(
            post("/plans/{planId}/route/alternatives", PLAN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"excludePlaceIds": ["%s"]}
                    """
                        .formatted(excludedId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.excludedPlaceIds[0]").value(excludedId.toString()))
        .andExpect(jsonPath("$.alternative.orderedPoints[0].name").value("남포동"))
        .andExpect(jsonPath("$.alternativeDurationMinutes").value(40))
        .andExpect(jsonPath("$.originalDurationMinutes").value(95))
        .andExpect(jsonPath("$.reducedMinutes").value(55));
  }

  @Test
  @DisplayName("본문 없이 요청하면 아무 장소도 빼지 않고 대체 경로를 계산한다")
  void generatesAlternativeWithoutABody() throws Exception {
    when(travelRouteService.generateAlternativeRoute(
            any(AuthenticatedUser.class),
            eq(PLAN_ID),
            eq(List.of()),
            nullable(RoutePoint.class),
            nullable(TransportType.class)))
        .thenReturn(
            AlternativeRouteResDto.of(
                TransportType.PUBLIC_TRANSPORT,
                VisitOrderResDto.of(
                    TransportType.PUBLIC_TRANSPORT, List.of(), List.of(), 0, List.of()),
                0,
                List.of(),
                List.of()));

    mockMvc.perform(post("/plans/{planId}/route/alternatives", PLAN_ID)).andExpect(status().isOk());

    verify(travelRouteService)
        .generateAlternativeRoute(
            any(AuthenticatedUser.class),
            eq(PLAN_ID),
            eq(List.of()),
            nullable(RoutePoint.class),
            nullable(TransportType.class));
  }

  @Test
  @DisplayName("출발 좌표가 범위를 벗어나면 400을 반환한다")
  void rejectsOutOfRangeStartInAlternative() throws Exception {
    mockMvc
        .perform(
            post("/plans/{planId}/route/alternatives", PLAN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"startLatitude": 200.0, "startLongitude": 129.0}
                    """))
        .andExpect(status().isBadRequest());
  }

  private PlanPlaceResDto planPlace(UUID id, int sequence, String name) {
    return new PlanPlaceResDto(
        id,
        PLAN_ID,
        sequence,
        name,
        "부산",
        35.1,
        129.1,
        PlaceProvider.GOOGLE,
        name,
        30,
        null,
        null,
        false);
  }

  private LocalValidatorFactoryBean validator() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    return validator;
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
