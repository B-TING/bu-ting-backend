package com.butingbe.domain.route.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.route.TravelRebootService;
import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.route.dto.response.TravelRebootResDto;
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
class TravelRebootControllerTest {

  private static final UUID PLAN_ID = UUID.fromString("33333333-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");
  private static final UUID GWANGALLI_ID = UUID.fromString("55555555-0000-0000-0000-000000000001");
  private static final UUID SEOMYEON_ID = UUID.fromString("55555555-0000-0000-0000-000000000002");

  private MockMvc mockMvc;

  @Mock private TravelRebootService travelRebootService;

  @InjectMocks private TravelRebootController travelRebootController;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc =
        MockMvcBuilders.standaloneSetup(travelRebootController)
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .setValidator(validator)
            .build();
  }

  @Test
  @DisplayName("현재 위치와 남은 시간으로 리부트 결과를 반환한다")
  void returnsRebootResult() throws Exception {
    RoutePoint current = RoutePoint.of("현재 위치", 35.2444, 129.2222);
    RoutePoint gwangalli = new RoutePoint(GWANGALLI_ID, "광안리", 35.1532, 129.1186);
    when(travelRebootService.reboot(
            any(AuthenticatedUser.class),
            eq(PLAN_ID),
            any(RoutePoint.class),
            eq(200),
            nullable(TransportType.class)))
        .thenReturn(
            new TravelRebootResDto(
                TransportType.PUBLIC_TRANSPORT,
                List.of(current, gwangalli),
                List.of(
                    new RouteLeg(current, gwangalli, TransportType.PUBLIC_TRANSPORT, 12_000, 90)),
                90,
                60,
                150,
                200,
                List.of(GWANGALLI_ID),
                List.of(SEOMYEON_ID),
                List.of(),
                List.of()));

    mockMvc
        .perform(
            post("/plans/{planId}/reboot", PLAN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "currentLatitude": 35.2444,
                      "currentLongitude": 129.2222,
                      "currentName": "현재 위치",
                      "availableMinutes": 200,
                      "transportType": "PUBLIC_TRANSPORT"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderedPoints[0].name").value("현재 위치"))
        .andExpect(jsonPath("$.orderedPoints[1].name").value("광안리"))
        .andExpect(jsonPath("$.totalTravelMinutes").value(90))
        .andExpect(jsonPath("$.totalStayMinutes").value(60))
        .andExpect(jsonPath("$.totalMinutes").value(150))
        .andExpect(jsonPath("$.reachablePlaceIds[0]").value(GWANGALLI_ID.toString()))
        .andExpect(jsonPath("$.droppedForTimePlaceIds[0]").value(SEOMYEON_ID.toString()));
  }

  @Test
  @DisplayName("출발 좌표를 현재 위치로 서비스에 전달한다")
  void passesCurrentPoint() throws Exception {
    when(travelRebootService.reboot(
            any(AuthenticatedUser.class),
            eq(PLAN_ID),
            any(RoutePoint.class),
            eq(300),
            eq(TransportType.WALK)))
        .thenReturn(
            new TravelRebootResDto(
                TransportType.WALK,
                List.of(),
                List.of(),
                0,
                0,
                0,
                300,
                List.of(),
                List.of(),
                List.of(),
                List.of()));

    mockMvc
        .perform(
            post("/plans/{planId}/reboot", PLAN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"currentLatitude": 35.15, "currentLongitude": 129.11,
                     "availableMinutes": 300, "transportType": "WALK"}
                    """))
        .andExpect(status().isOk());

    ArgumentCaptor<RoutePoint> captor = ArgumentCaptor.forClass(RoutePoint.class);
    verify(travelRebootService)
        .reboot(
            any(AuthenticatedUser.class),
            eq(PLAN_ID),
            captor.capture(),
            eq(300),
            eq(TransportType.WALK));
    assertThat(captor.getValue().latitude()).isEqualTo(35.15);
    assertThat(captor.getValue().name()).isEqualTo("현재 위치");
  }

  @Test
  @DisplayName("현재 좌표가 없으면 400을 반환한다")
  void rejectsMissingCoordinates() throws Exception {
    mockMvc
        .perform(
            post("/plans/{planId}/reboot", PLAN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"availableMinutes": 300}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("남은 시간이 0 이하이면 400을 반환한다")
  void rejectsNonPositiveAvailableMinutes() throws Exception {
    mockMvc
        .perform(
            post("/plans/{planId}/reboot", PLAN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"currentLatitude": 35.15, "currentLongitude": 129.11, "availableMinutes": 0}
                    """))
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
