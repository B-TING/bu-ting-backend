package com.butingbe.domain.travelrecord.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelrecord.dto.request.PlaceReviewCreateReqDto;
import com.butingbe.domain.travelrecord.dto.request.PlaceReviewUpdateReqDto;
import com.butingbe.domain.travelrecord.dto.response.PlaceReviewResDto;
import com.butingbe.domain.travelrecord.service.TravelRecordService;
import com.butingbe.global.error.exception.UnauthenticatedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
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
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class PlanPlaceReviewControllerTest {

  private static final UUID TRAVEL_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID PLAN_PLACE_ID = UUID.fromString("55555555-0000-0000-0000-000000000001");
  private static final UUID REVIEW_ID = UUID.fromString("66666666-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  private static final String BASE_PATH = "/travels/{travelId}/plans/places/{planPlaceId}/review";

  private MockMvc mockMvc;

  @Mock private TravelRecordService travelRecordService;

  @InjectMocks private PlanPlaceReviewController planPlaceReviewController;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(planPlaceReviewController)
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(
                new MappingJackson2HttpMessageConverter(
                    new ObjectMapper().findAndRegisterModules()))
            .build();
  }

  @Test
  @DisplayName("장소 리뷰를 등록하면 201과 생성된 리뷰를 반환한다")
  void createPlaceReviewReturnsCreated() throws Exception {
    when(travelRecordService.createPlaceReview(
            any(), eq(TRAVEL_ID), eq(PLAN_PLACE_ID), any(PlaceReviewCreateReqDto.class)))
        .thenReturn(review());

    mockMvc
        .perform(
            post(BASE_PATH, TRAVEL_ID, PLAN_PLACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "rating": 5,
                      "content": "야경이 좋았다",
                      "tags": ["야경", "가족"],
                      "stayMinutes": 90,
                      "mediaFileKeys": ["uploads/1.jpg"]
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.placeReviewId").value(REVIEW_ID.toString()))
        .andExpect(jsonPath("$.rating").value(5))
        .andExpect(jsonPath("$.tags[0]").value("야경"))
        .andExpect(jsonPath("$.mediaUrls[0]").value("https://cdn.example.com/1.jpg"));
  }

  @Test
  @DisplayName("rating이 범위를 벗어나면 400을 반환한다")
  void createPlaceReviewRejectsOutOfRangeRating() throws Exception {
    mockMvc
        .perform(
            post(BASE_PATH, TRAVEL_ID, PLAN_PLACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"rating": 6, "content": "잘못된 평점"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("장소 리뷰를 조회하면 리뷰 정보를 반환한다")
  void getPlaceReviewReturnsReview() throws Exception {
    when(travelRecordService.getPlaceReview(any(), eq(TRAVEL_ID), eq(PLAN_PLACE_ID)))
        .thenReturn(review());

    mockMvc
        .perform(get(BASE_PATH, TRAVEL_ID, PLAN_PLACE_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.planPlaceId").value(PLAN_PLACE_ID.toString()))
        .andExpect(jsonPath("$.stayMinutes").value(90))
        .andExpect(jsonPath("$.content").value("야경이 좋았다"));
  }

  @Test
  @DisplayName("장소 리뷰를 수정하면 갱신된 리뷰를 반환한다")
  void updatePlaceReviewReturnsUpdatedReview() throws Exception {
    when(travelRecordService.updatePlaceReview(
            any(), eq(TRAVEL_ID), eq(PLAN_PLACE_ID), any(PlaceReviewUpdateReqDto.class)))
        .thenReturn(review());

    mockMvc
        .perform(
            patch(BASE_PATH, TRAVEL_ID, PLAN_PLACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"rating": 4, "content": "수정된 리뷰"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.placeReviewId").value(REVIEW_ID.toString()));

    verify(travelRecordService)
        .updatePlaceReview(
            any(), eq(TRAVEL_ID), eq(PLAN_PLACE_ID), any(PlaceReviewUpdateReqDto.class));
  }

  @Test
  @DisplayName("장소 리뷰를 삭제하면 204를 반환한다")
  void deletePlaceReviewReturnsNoContent() throws Exception {
    mockMvc.perform(delete(BASE_PATH, TRAVEL_ID, PLAN_PLACE_ID)).andExpect(status().isNoContent());

    verify(travelRecordService).deletePlaceReview(any(), eq(TRAVEL_ID), eq(PLAN_PLACE_ID));
  }

  @Test
  @DisplayName("인증되지 않은 사용자의 요청은 모두 UnauthenticatedException을 던진다")
  void rejectsUnauthenticatedUser() {
    assertThatThrownBy(
            () -> planPlaceReviewController.createPlaceReview(null, TRAVEL_ID, PLAN_PLACE_ID, null))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(
            () -> planPlaceReviewController.getPlaceReview(null, TRAVEL_ID, PLAN_PLACE_ID))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(
            () -> planPlaceReviewController.updatePlaceReview(null, TRAVEL_ID, PLAN_PLACE_ID, null))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(
            () -> planPlaceReviewController.deletePlaceReview(null, TRAVEL_ID, PLAN_PLACE_ID))
        .isInstanceOf(UnauthenticatedException.class);
  }

  private PlaceReviewResDto review() {
    return new PlaceReviewResDto(
        REVIEW_ID,
        PLAN_PLACE_ID,
        null,
        5,
        90,
        "야경이 좋았다",
        List.of("야경", "가족"),
        List.of("https://cdn.example.com/1.jpg"),
        LocalDateTime.of(2026, 9, 5, 20, 0),
        LocalDateTime.of(2026, 9, 5, 20, 0));
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
        return new AuthenticatedUser(USER_ID, "user@example.com", "tester", List.of());
      }
    };
  }
}
