package com.butingbe.domain.travel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.dto.request.PlanCreateReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceCreateReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceSequenceUpdateReqDto;
import com.butingbe.domain.travel.dto.request.PlanPlaceUpdateReqDto;
import com.butingbe.domain.travel.dto.request.TravelCreateReqDto;
import com.butingbe.domain.travel.dto.response.PlanPlaceResDto;
import com.butingbe.domain.travel.dto.response.PlanResDto;
import com.butingbe.domain.travel.dto.response.TravelPlansResDto;
import com.butingbe.domain.travel.dto.response.TravelResDto;
import com.butingbe.domain.travel.entity.TravelStatus;
import com.butingbe.domain.travel.service.TravelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class TravelControllerTest {

  private MockMvc mockMvc;
  private TravelController travelController;
  private FakeTravelService travelService;

  @BeforeEach
  void setUp() {
    travelService = new FakeTravelService();
    travelController = new TravelController(travelService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(travelController)
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(
                new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                    new ObjectMapper().findAndRegisterModules()))
            .build();
  }

  @Test
  @DisplayName("여행 생성 요청을 service에 위임하고 201을 반환한다")
  void createTravel() {
    var response =
        travelController.createTravel(
            authenticatedUser(),
            new TravelCreateReqDto(
                "Busan",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 3),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getBody().id()).isEqualTo(FakeTravelService.TRAVEL_ID);
    assertThat(response.getBody().title()).isEqualTo("Busan");
    assertThat(travelService.createTravelCalled).isTrue();
  }

  @Test
  @DisplayName("여행의 일자별 계획 목록을 조회한다")
  void getTravelPlans() throws Exception {
    mockMvc
        .perform(get("/travels/{travelId}/plans", FakeTravelService.TRAVEL_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.travelId").value(FakeTravelService.TRAVEL_ID.toString()))
        .andExpect(jsonPath("$.title").value("Busan"));
  }

  @Test
  @DisplayName("여행 일자 생성 요청을 service에 위임하고 201을 반환한다")
  void createPlan() {
    var response =
        travelController.createPlan(
            authenticatedUser(),
            FakeTravelService.TRAVEL_ID,
            new PlanCreateReqDto(1, LocalDate.of(2026, 8, 1)));

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getBody().planId()).isEqualTo(FakeTravelService.PLAN_ID);
    assertThat(response.getBody().dayNumber()).isEqualTo(1);
  }

  @Test
  @DisplayName("여행 일자 삭제 요청을 service에 위임한다")
  void deletePlan() throws Exception {
    mockMvc
        .perform(
            delete(
                "/travels/{travelId}/plans/{planId}",
                FakeTravelService.TRAVEL_ID,
                FakeTravelService.PLAN_ID))
        .andExpect(status().isNoContent());

    assertThat(travelService.deletedPlanId).isEqualTo(FakeTravelService.PLAN_ID);
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
        return new AuthenticatedUser(UUID.randomUUID(), "user@example.com", "tester", List.of());
      }
    };
  }

  private AuthenticatedUser authenticatedUser() {
    return new AuthenticatedUser(UUID.randomUUID(), "user@example.com", "tester", List.of());
  }

  static class FakeTravelService implements TravelService {
    static final UUID TRAVEL_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID PLAN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    boolean createTravelCalled;
    UUID deletedPlanId;

    @Override
    public TravelResDto createTravel(
        AuthenticatedUser authenticatedUser, TravelCreateReqDto request) {
      createTravelCalled = true;
      return new TravelResDto(
          TRAVEL_ID,
          request.title(),
          request.startDate(),
          request.endDate(),
          TravelStatus.PLANNED,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null);
    }

    @Override
    public TravelPlansResDto getTravelPlans(AuthenticatedUser authenticatedUser, UUID travelId) {
      return new TravelPlansResDto(travelId, "Busan", List.of());
    }

    @Override
    public PlanResDto createPlan(
        AuthenticatedUser authenticatedUser, UUID travelId, PlanCreateReqDto request) {
      return new PlanResDto(PLAN_ID, travelId, request.dayNumber(), request.visitDate());
    }

    @Override
    public void deletePlan(AuthenticatedUser authenticatedUser, UUID travelId, UUID planId) {
      deletedPlanId = planId;
    }

    @Override
    public PlanPlaceResDto createPlanPlace(
        AuthenticatedUser authenticatedUser, UUID planId, PlanPlaceCreateReqDto request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<PlanPlaceResDto> getPlanPlaces(AuthenticatedUser authenticatedUser, UUID planId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PlanPlaceResDto updatePlanPlace(
        AuthenticatedUser authenticatedUser, UUID planPlaceId, PlanPlaceUpdateReqDto request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<PlanPlaceResDto> updatePlanPlaceSequence(
        AuthenticatedUser authenticatedUser, UUID planId, PlanPlaceSequenceUpdateReqDto request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void deletePlanPlace(AuthenticatedUser authenticatedUser, UUID planPlaceId) {
      throw new UnsupportedOperationException();
    }
  }
}
