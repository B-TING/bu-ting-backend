package com.butingbe.domain.travelrecord.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelrecord.dto.response.PlaceReviewSummaryResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordFeedPageResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordFeedResDto;
import com.butingbe.domain.travelrecord.service.TravelRecordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class PublicPlaceControllersTest {

  private static final UUID RECORD_ID = UUID.fromString("44444444-0000-0000-0000-000000000001");
  private static final UUID TRAVEL_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  private MockMvc mockMvc;

  @Mock private TravelRecordService travelRecordService;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new PublicPlaceTravelRecordController(travelRecordService),
                new PublicPlaceReviewController(travelRecordService))
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(
                new MappingJackson2HttpMessageConverter(
                    new ObjectMapper().findAndRegisterModules()))
            .build();
  }

  @Test
  @DisplayName("장소별 여행 기록 피드를 커서와 함께 반환한다")
  void getTravelRecordsByPlaceReturnsFeedPage() throws Exception {
    when(travelRecordService.getTravelRecordsByPlace(
            any(AuthenticatedUser.class),
            eq("google-place-id"),
            nullable(String.class),
            nullable(Integer.class)))
        .thenReturn(new TravelRecordFeedPageResDto(List.of(feedItem()), "next-cursor", true));

    mockMvc
        .perform(get("/places/travel-records").param("placeId", "google-place-id"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].travelRecordId").value(RECORD_ID.toString()))
        .andExpect(jsonPath("$.items[0].title").value("부산 3일"))
        .andExpect(jsonPath("$.nextCursor").value("next-cursor"))
        .andExpect(jsonPath("$.hasNext").value(true));
  }

  @Test
  @DisplayName("커서와 size 파라미터를 서비스에 그대로 전달한다")
  void getTravelRecordsByPlacePassesCursorAndSize() throws Exception {
    when(travelRecordService.getTravelRecordsByPlace(
            any(AuthenticatedUser.class),
            eq("google-place-id"),
            nullable(String.class),
            nullable(Integer.class)))
        .thenReturn(new TravelRecordFeedPageResDto(List.of(), null, false));

    mockMvc
        .perform(
            get("/places/travel-records")
                .param("placeId", "google-place-id")
                .param("cursor", "c1")
                .param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNext").value(false));

    verify(travelRecordService)
        .getTravelRecordsByPlace(
            any(AuthenticatedUser.class), eq("google-place-id"), eq("c1"), eq(5));
  }

  @Test
  @DisplayName("placeId가 없으면 장소별 기록 조회는 400을 반환한다")
  void getTravelRecordsByPlaceRequiresPlaceId() throws Exception {
    mockMvc.perform(get("/places/travel-records")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("장소 리뷰 요약을 반환한다")
  void getPlaceReviewSummaryReturnsSummary() throws Exception {
    when(travelRecordService.getPlaceReviewSummary("google-place-id"))
        .thenReturn(
            PlaceReviewSummaryResDto.of(
                "google-place-id",
                4.5,
                Map.of(5, 1L, 4, 1L),
                List.of(
                    new PlaceReviewSummaryResDto.PlaceReviewItemResDto(
                        UUID.fromString("66666666-0000-0000-0000-000000000001"),
                        RECORD_ID,
                        "부산 3일",
                        USER_ID,
                        "리뷰어",
                        UUID.fromString("77777777-0000-0000-0000-000000000001"),
                        "광안리",
                        5,
                        90,
                        "좋았어요",
                        List.of("야경"),
                        List.of(),
                        LocalDateTime.of(2026, 9, 5, 20, 0),
                        LocalDateTime.of(2026, 9, 5, 20, 0)))));

    mockMvc
        .perform(get("/places/reviews").param("placeId", "google-place-id"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.placeId").value("google-place-id"))
        .andExpect(jsonPath("$.averageRating").value(4.5))
        .andExpect(jsonPath("$.reviewCount").value(1))
        .andExpect(jsonPath("$.reviews[0].content").value("좋았어요"));
  }

  @Test
  @DisplayName("placeId가 없으면 리뷰 요약 조회는 400을 반환한다")
  void getPlaceReviewSummaryRequiresPlaceId() throws Exception {
    mockMvc.perform(get("/places/reviews")).andExpect(status().isBadRequest());
  }

  private TravelRecordFeedResDto feedItem() {
    return new TravelRecordFeedResDto(
        RECORD_ID,
        TRAVEL_ID,
        USER_ID,
        "작성자",
        "부산 3일",
        "즐거웠다",
        "https://cdn.example.com/cover.jpg",
        5,
        LocalDate.of(2026, 9, 1),
        LocalDate.of(2026, 9, 3),
        LocalDateTime.of(2026, 9, 5, 12, 0),
        3L,
        10L,
        false);
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
