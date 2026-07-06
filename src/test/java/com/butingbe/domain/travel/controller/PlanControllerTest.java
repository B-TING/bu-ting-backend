package com.butingbe.domain.travel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travel.service.TravelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class PlanControllerTest {

  private MockMvc mockMvc;
  private PlanController planController;
  private FakeTravelService travelService;

  @BeforeEach
  void setUp() {
    travelService = new FakeTravelService();
    planController = new PlanController(travelService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(planController)
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(
                new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                    new ObjectMapper().findAndRegisterModules()))
            .build();
  }

  @Test
  @DisplayName("plan에 속한 장소 목록을 조회한다")
  void getPlanPlaces() throws Exception {
    mockMvc
        .perform(get("/plans/{planId}/places", FakeTravelService.PLAN_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].planPlaceId").value(FakeTravelService.PLACE_ID.toString()))
        .andExpect(jsonPath("$[0].sequence").value(1));
  }

  @Test
  @DisplayName("plan에 장소를 추가한다")
  void createPlanPlace() throws Exception {
    mockMvc
        .perform(
            post("/plans/{planId}/places", FakeTravelService.PLAN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sequence": 1,
                      "placeName": "Busan Station",
                      "address": "Busan",
                      "latitude": 35.115,
                      "longitude": 129.041,
                      "provider": "GOOGLE",
                      "providerPlaceId": "google-place-id",
                      "durationMinutes": 30
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.planPlaceId").value(FakeTravelService.PLACE_ID.toString()));
  }

  @Test
  @DisplayName("장소의 메모와 방문 예정 시간을 수정한다")
  void updatePlanPlace() {
    var response =
        planController.updatePlanPlace(
            authenticatedUser(),
            FakeTravelService.PLACE_ID,
            new PlanPlaceUpdateReqDto(45, LocalTime.of(13, 30), "Lunch"));

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody().planPlaceId()).isEqualTo(FakeTravelService.PLACE_ID);
    assertThat(travelService.updatedPlanPlaceRequest.durationMinutes()).isEqualTo(45);
    assertThat(travelService.updatedPlanPlaceRequest.scheduledTime())
        .isEqualTo(LocalTime.of(13, 30));
    assertThat(travelService.updatedPlanPlaceRequest.memo()).isEqualTo("Lunch");
  }

  @Test
  @DisplayName("드래그 앤 드롭 결과 순서 배열을 service에 전달한다")
  void updatePlanPlaceSequence() throws Exception {
    UUID anotherPlaceId = UUID.fromString("30000000-0000-0000-0000-000000000002");

    mockMvc
        .perform(
            patch("/plans/{planId}/places/sequence", FakeTravelService.PLAN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "planPlaceIds": ["%s", "%s"]
                    }
                    """
                        .formatted(anotherPlaceId, FakeTravelService.PLACE_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].planPlaceId").value(anotherPlaceId.toString()))
        .andExpect(jsonPath("$[0].sequence").value(1));
  }

  @Test
  @DisplayName("장소 삭제 요청을 service에 위임한다")
  void deletePlanPlace() throws Exception {
    mockMvc
        .perform(delete("/plans/places/{planPlaceId}", FakeTravelService.PLACE_ID))
        .andExpect(status().isNoContent());

    assertThat(travelService.deletedPlanPlaceId).isEqualTo(FakeTravelService.PLACE_ID);
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
    static final UUID PLAN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final UUID PLACE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    UUID deletedPlanPlaceId;
    PlanPlaceUpdateReqDto updatedPlanPlaceRequest;

    @Override
    public TravelResDto createTravel(
        AuthenticatedUser authenticatedUser, TravelCreateReqDto request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public TravelPlansResDto getTravelPlans(AuthenticatedUser authenticatedUser, UUID travelId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PlanResDto createPlan(
        AuthenticatedUser authenticatedUser, UUID travelId, PlanCreateReqDto request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void deletePlan(AuthenticatedUser authenticatedUser, UUID travelId, UUID planId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PlanPlaceResDto createPlanPlace(
        AuthenticatedUser authenticatedUser, UUID planId, PlanPlaceCreateReqDto request) {
      return placeResponse(PLACE_ID, 1);
    }

    @Override
    public List<PlanPlaceResDto> getPlanPlaces(AuthenticatedUser authenticatedUser, UUID planId) {
      return List.of(placeResponse(PLACE_ID, 1));
    }

    @Override
    public PlanPlaceResDto updatePlanPlace(
        AuthenticatedUser authenticatedUser, UUID planPlaceId, PlanPlaceUpdateReqDto request) {
      updatedPlanPlaceRequest = request;
      return placeResponse(planPlaceId, 1);
    }

    @Override
    public List<PlanPlaceResDto> updatePlanPlaceSequence(
        AuthenticatedUser authenticatedUser, UUID planId, PlanPlaceSequenceUpdateReqDto request) {
      return java.util.stream.IntStream.range(0, request.planPlaceIds().size())
          .mapToObj(index -> placeResponse(request.planPlaceIds().get(index), index + 1))
          .toList();
    }

    @Override
    public void deletePlanPlace(AuthenticatedUser authenticatedUser, UUID planPlaceId) {
      deletedPlanPlaceId = planPlaceId;
    }

    private PlanPlaceResDto placeResponse(UUID planPlaceId, Integer sequence) {
      return new PlanPlaceResDto(
          planPlaceId,
          PLAN_ID,
          sequence,
          "Busan Station",
          "Busan",
          35.115,
          129.041,
          PlaceProvider.GOOGLE,
          "google-place-id",
          30,
          null,
          null,
          false);
    }
  }
}
