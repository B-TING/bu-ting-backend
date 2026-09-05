package com.butingbe.domain.travelrecord.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordUpdateReqDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordResDto;
import com.butingbe.domain.travelrecord.entity.TravelRecordStatus;
import com.butingbe.domain.travelrecord.service.TravelRecordService;
import com.butingbe.global.error.exception.UnauthenticatedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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
class TravelRecordControllerTest {

  private static final UUID TRAVEL_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID RECORD_ID = UUID.fromString("44444444-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  private MockMvc mockMvc;

  @Mock private TravelRecordService travelRecordService;

  @InjectMocks private TravelRecordController travelRecordController;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(travelRecordController)
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(
                new MappingJackson2HttpMessageConverter(
                    new ObjectMapper().findAndRegisterModules()))
            .build();
  }

  @Test
  @DisplayName("여행 기록 초안을 생성하면 201과 DRAFT 상태를 반환한다")
  void createDraftReturnsCreated() throws Exception {
    when(travelRecordService.createDraft(any(), eq(TRAVEL_ID), any()))
        .thenReturn(record(TravelRecordStatus.DRAFT, null));

    mockMvc
        .perform(
            post("/travels/{travelId}/records", TRAVEL_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "부산 3일",
                      "content": "즐거웠다",
                      "coverImageUrl": "https://cdn.example.com/cover.jpg",
                      "overallRating": 5
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.travelRecordId").value(RECORD_ID.toString()))
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.publishedAt").doesNotExist());
  }

  @Test
  @DisplayName("본문 없이 초안을 생성해도 201을 반환한다")
  void createDraftAcceptsEmptyBody() throws Exception {
    when(travelRecordService.createDraft(any(), eq(TRAVEL_ID), eq(null)))
        .thenReturn(record(TravelRecordStatus.DRAFT, null));

    mockMvc
        .perform(post("/travels/{travelId}/records", TRAVEL_ID))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("DRAFT"));
  }

  @Test
  @DisplayName("여행 기록 초안을 조회하면 상세 정보를 반환한다")
  void getDraftReturnsRecord() throws Exception {
    when(travelRecordService.getDraft(any(), eq(TRAVEL_ID), eq(RECORD_ID)))
        .thenReturn(record(TravelRecordStatus.DRAFT, null));

    mockMvc
        .perform(get("/travels/{travelId}/records/{travelRecordId}", TRAVEL_ID, RECORD_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.travelRecordId").value(RECORD_ID.toString()))
        .andExpect(jsonPath("$.authorNickname").value("작성자"))
        .andExpect(jsonPath("$.title").value("부산 3일"))
        .andExpect(jsonPath("$.overallRating").value(5));
  }

  @Test
  @DisplayName("여행 기록 초안을 수정하면 갱신된 정보를 반환한다")
  void updateDraftReturnsUpdatedRecord() throws Exception {
    when(travelRecordService.updateDraft(
            any(), eq(TRAVEL_ID), eq(RECORD_ID), any(TravelRecordUpdateReqDto.class)))
        .thenReturn(record(TravelRecordStatus.DRAFT, null));

    mockMvc
        .perform(
            patch("/travels/{travelId}/records/{travelRecordId}", TRAVEL_ID, RECORD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "부산 3일", "overallRating": 5}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("부산 3일"));

    verify(travelRecordService)
        .updateDraft(any(), eq(TRAVEL_ID), eq(RECORD_ID), any(TravelRecordUpdateReqDto.class));
  }

  @Test
  @DisplayName("여행 기록을 발행하면 PUBLISHED 상태와 발행 시각을 반환한다")
  void publishReturnsPublishedRecord() throws Exception {
    LocalDateTime publishedAt = LocalDateTime.of(2026, 9, 5, 12, 0);
    when(travelRecordService.publish(any(), eq(TRAVEL_ID), eq(RECORD_ID)))
        .thenReturn(record(TravelRecordStatus.PUBLISHED, publishedAt));

    mockMvc
        .perform(post("/travels/{travelId}/records/{travelRecordId}/publish", TRAVEL_ID, RECORD_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"))
        .andExpect(jsonPath("$.publishedAt").exists());
  }

  @Test
  @DisplayName("인증되지 않은 사용자의 요청은 모두 UnauthenticatedException을 던진다")
  void rejectsUnauthenticatedUser() {
    assertThatThrownBy(() -> travelRecordController.createDraft(null, TRAVEL_ID, null))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> travelRecordController.getDraft(null, TRAVEL_ID, RECORD_ID))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> travelRecordController.updateDraft(null, TRAVEL_ID, RECORD_ID, null))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> travelRecordController.publish(null, TRAVEL_ID, RECORD_ID))
        .isInstanceOf(UnauthenticatedException.class);
  }

  private TravelRecordResDto record(TravelRecordStatus status, LocalDateTime publishedAt) {
    return new TravelRecordResDto(
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
        status,
        publishedAt,
        0L,
        0L,
        false,
        List.of());
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
