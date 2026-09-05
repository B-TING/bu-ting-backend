package com.butingbe.domain.travelrecord.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.dto.response.TravelPlansResDto;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordCloneToTravelReqDto;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordCommentCreateReqDto;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordCommentUpdateReqDto;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordFeedSort;
import com.butingbe.domain.travelrecord.dto.request.TravelRecordUpdateReqDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordBookmarkResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordCommentResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordFeedPageResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordFeedResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordLikeResDto;
import com.butingbe.domain.travelrecord.dto.response.TravelRecordManageResDto;
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
class PublicTravelRecordControllerTest {

  private static final UUID TRAVEL_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
  private static final UUID RECORD_ID = UUID.fromString("44444444-0000-0000-0000-000000000001");
  private static final UUID COMMENT_ID = UUID.fromString("77777777-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  private MockMvc mockMvc;

  @Mock private TravelRecordService travelRecordService;

  @InjectMocks private PublicTravelRecordController publicTravelRecordController;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(publicTravelRecordController)
            .setCustomArgumentResolvers(authenticatedUserResolver())
            .setMessageConverters(
                new MappingJackson2HttpMessageConverter(
                    new ObjectMapper().findAndRegisterModules()))
            .build();
  }

  @Test
  @DisplayName("피드를 조회하면 항목과 다음 커서를 반환한다")
  void getLatestFeedReturnsPage() throws Exception {
    when(travelRecordService.getLatestFeed(
            any(AuthenticatedUser.class),
            nullable(String.class),
            nullable(Integer.class),
            nullable(String.class),
            nullable(String.class),
            nullable(LocalDate.class),
            nullable(LocalDate.class),
            nullable(String.class),
            nullable(String.class),
            nullable(TravelRecordFeedSort.class)))
        .thenReturn(new TravelRecordFeedPageResDto(List.of(feedItem()), "next-cursor", true));

    mockMvc
        .perform(get("/travel-records"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].travelRecordId").value(RECORD_ID.toString()))
        .andExpect(jsonPath("$.items[0].authorNickname").value("작성자"))
        .andExpect(jsonPath("$.nextCursor").value("next-cursor"))
        .andExpect(jsonPath("$.hasNext").value(true));
  }

  @Test
  @DisplayName("피드 필터 파라미터가 서비스에 그대로 전달된다")
  void getLatestFeedPassesFilters() throws Exception {
    when(travelRecordService.getLatestFeed(
            any(AuthenticatedUser.class),
            nullable(String.class),
            nullable(Integer.class),
            nullable(String.class),
            nullable(String.class),
            nullable(LocalDate.class),
            nullable(LocalDate.class),
            nullable(String.class),
            nullable(String.class),
            nullable(TravelRecordFeedSort.class)))
        .thenReturn(new TravelRecordFeedPageResDto(List.of(), null, false));

    mockMvc
        .perform(
            get("/travel-records")
                .param("cursor", "c1")
                .param("size", "10")
                .param("keyword", "부산")
                .param("placeId", "place-1")
                .param("travelStartDate", "2026-09-01")
                .param("travelEndDate", "2026-09-03")
                .param("region", "부산광역시")
                .param("city", "해운대구")
                .param("sort", "MOST_LIKED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasNext").value(false));

    verify(travelRecordService)
        .getLatestFeed(
            any(AuthenticatedUser.class),
            eq("c1"),
            eq(10),
            eq("부산"),
            eq("place-1"),
            eq(LocalDate.of(2026, 9, 1)),
            eq(LocalDate.of(2026, 9, 3)),
            eq("부산광역시"),
            eq("해운대구"),
            eq(TravelRecordFeedSort.MOST_LIKED));
  }

  @Test
  @DisplayName("내 기록 목록을 조회하면 관리용 정보를 반환한다")
  void getMyRecordsReturnsManageList() throws Exception {
    when(travelRecordService.getMyRecords(any()))
        .thenReturn(
            List.of(
                new TravelRecordManageResDto(
                    RECORD_ID,
                    TRAVEL_ID,
                    USER_ID,
                    "부산 3일",
                    "즐거웠다",
                    "https://cdn.example.com/cover.jpg",
                    5,
                    LocalDate.of(2026, 9, 1),
                    LocalDate.of(2026, 9, 3),
                    TravelRecordStatus.PUBLISHED,
                    LocalDateTime.of(2026, 9, 5, 12, 0),
                    3L,
                    10L,
                    LocalDateTime.of(2026, 9, 4, 9, 0),
                    LocalDateTime.of(2026, 9, 5, 12, 0))));

    mockMvc
        .perform(get("/travel-records/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].travelRecordId").value(RECORD_ID.toString()))
        .andExpect(jsonPath("$[0].status").value("PUBLISHED"))
        .andExpect(jsonPath("$[0].likeCount").value(3));
  }

  @Test
  @DisplayName("내 북마크 목록을 조회하면 북마크한 기록을 반환한다")
  void getMyBookmarkedRecordsReturnsBookmarks() throws Exception {
    when(travelRecordService.getMyBookmarkedRecords(any())).thenReturn(List.of(bookmark()));

    mockMvc
        .perform(get("/travel-records/me/bookmarks"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].bookmarkId").exists())
        .andExpect(jsonPath("$[0].travelRecord.travelRecordId").value(RECORD_ID.toString()));
  }

  @Test
  @DisplayName("내 기록 단건을 조회하면 상세 정보를 반환한다")
  void getMyRecordReturnsRecord() throws Exception {
    when(travelRecordService.getMyRecord(any(), eq(RECORD_ID)))
        .thenReturn(record(TravelRecordStatus.DRAFT));

    mockMvc
        .perform(get("/travel-records/me/{travelRecordId}", RECORD_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.travelRecordId").value(RECORD_ID.toString()))
        .andExpect(jsonPath("$.status").value("DRAFT"));
  }

  @Test
  @DisplayName("내 기록을 수정하면 갱신된 정보를 반환한다")
  void updateMyRecordReturnsUpdatedRecord() throws Exception {
    when(travelRecordService.updateMyRecord(
            any(), eq(RECORD_ID), any(TravelRecordUpdateReqDto.class)))
        .thenReturn(record(TravelRecordStatus.PUBLISHED));

    mockMvc
        .perform(
            patch("/travel-records/me/{travelRecordId}", RECORD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "부산 3일", "overallRating": 5}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("부산 3일"));
  }

  @Test
  @DisplayName("내 기록을 숨기면 HIDDEN 상태를 반환한다")
  void hideMyRecordReturnsHidden() throws Exception {
    when(travelRecordService.hideMyRecord(any(), eq(RECORD_ID)))
        .thenReturn(record(TravelRecordStatus.HIDDEN));

    mockMvc
        .perform(post("/travel-records/me/{travelRecordId}/hide", RECORD_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("HIDDEN"));
  }

  @Test
  @DisplayName("숨긴 기록을 다시 공개하면 PUBLISHED 상태를 반환한다")
  void republishMyRecordReturnsPublished() throws Exception {
    when(travelRecordService.republishMyRecord(any(), eq(RECORD_ID)))
        .thenReturn(record(TravelRecordStatus.PUBLISHED));

    mockMvc
        .perform(post("/travel-records/me/{travelRecordId}/republish", RECORD_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"));
  }

  @Test
  @DisplayName("기록을 북마크하면 201과 북마크 정보를 반환한다")
  void bookmarkTravelRecordReturnsCreated() throws Exception {
    when(travelRecordService.bookmarkTravelRecord(any(), eq(RECORD_ID))).thenReturn(bookmark());

    mockMvc
        .perform(post("/travel-records/{travelRecordId}/bookmarks", RECORD_ID))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.travelRecord.travelRecordId").value(RECORD_ID.toString()));
  }

  @Test
  @DisplayName("북마크를 해제하면 204를 반환한다")
  void unbookmarkTravelRecordReturnsNoContent() throws Exception {
    mockMvc
        .perform(delete("/travel-records/{travelRecordId}/bookmarks", RECORD_ID))
        .andExpect(status().isNoContent());

    verify(travelRecordService).unbookmarkTravelRecord(any(), eq(RECORD_ID));
  }

  @Test
  @DisplayName("기록에 좋아요를 누르면 201과 좋아요 수를 반환한다")
  void likeTravelRecordReturnsCreated() throws Exception {
    when(travelRecordService.likeTravelRecord(any(), eq(RECORD_ID)))
        .thenReturn(
            new TravelRecordLikeResDto(
                UUID.fromString("88888888-0000-0000-0000-000000000001"),
                RECORD_ID,
                LocalDateTime.of(2026, 9, 5, 13, 0),
                4L));

    mockMvc
        .perform(post("/travel-records/{travelRecordId}/likes", RECORD_ID))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.travelRecordId").value(RECORD_ID.toString()))
        .andExpect(jsonPath("$.likeCount").value(4));
  }

  @Test
  @DisplayName("좋아요를 취소하면 204를 반환한다")
  void unlikeTravelRecordReturnsNoContent() throws Exception {
    mockMvc
        .perform(delete("/travel-records/{travelRecordId}/likes", RECORD_ID))
        .andExpect(status().isNoContent());

    verify(travelRecordService).unlikeTravelRecord(any(), eq(RECORD_ID));
  }

  @Test
  @DisplayName("댓글을 등록하면 201과 생성된 댓글을 반환한다")
  void createCommentReturnsCreated() throws Exception {
    when(travelRecordService.createComment(
            any(), eq(RECORD_ID), any(TravelRecordCommentCreateReqDto.class)))
        .thenReturn(comment("좋은 기록이네요"));

    mockMvc
        .perform(
            post("/travel-records/{travelRecordId}/comments", RECORD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"content": "좋은 기록이네요"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.commentId").value(COMMENT_ID.toString()))
        .andExpect(jsonPath("$.content").value("좋은 기록이네요"));
  }

  @Test
  @DisplayName("댓글 내용이 비어 있으면 400을 반환한다")
  void createCommentRejectsBlankContent() throws Exception {
    mockMvc
        .perform(
            post("/travel-records/{travelRecordId}/comments", RECORD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"content": "  "}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("댓글 목록은 인증 없이도 조회할 수 있다")
  void getCommentsIsPubliclyAccessible() throws Exception {
    when(travelRecordService.getComments(RECORD_ID)).thenReturn(List.of(comment("첫 댓글")));

    mockMvc
        .perform(get("/travel-records/{travelRecordId}/comments", RECORD_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].content").value("첫 댓글"))
        .andExpect(jsonPath("$[0].authorNickname").value("댓글작성자"));
  }

  @Test
  @DisplayName("댓글을 수정하면 갱신된 댓글을 반환한다")
  void updateCommentReturnsUpdatedComment() throws Exception {
    when(travelRecordService.updateComment(
            any(), eq(RECORD_ID), eq(COMMENT_ID), any(TravelRecordCommentUpdateReqDto.class)))
        .thenReturn(comment("수정된 댓글"));

    mockMvc
        .perform(
            patch("/travel-records/{travelRecordId}/comments/{commentId}", RECORD_ID, COMMENT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"content": "수정된 댓글"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").value("수정된 댓글"));
  }

  @Test
  @DisplayName("댓글을 삭제하면 204를 반환한다")
  void deleteCommentReturnsNoContent() throws Exception {
    mockMvc
        .perform(
            delete("/travel-records/{travelRecordId}/comments/{commentId}", RECORD_ID, COMMENT_ID))
        .andExpect(status().isNoContent());

    verify(travelRecordService).deleteComment(any(), eq(RECORD_ID), eq(COMMENT_ID));
  }

  @Test
  @DisplayName("기록을 내 여행으로 복제하면 201과 생성된 여행 일정을 반환한다")
  void cloneToTravelReturnsCreated() throws Exception {
    when(travelRecordService.cloneToTravel(
            any(), eq(RECORD_ID), any(TravelRecordCloneToTravelReqDto.class)))
        .thenReturn(new TravelPlansResDto(TRAVEL_ID, "부산 3일", List.of()));

    mockMvc
        .perform(
            post("/travel-records/{travelRecordId}/clone-to-travel", RECORD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "부산 3일", "startDate": "2026-10-01"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.travelId").value(TRAVEL_ID.toString()))
        .andExpect(jsonPath("$.title").value("부산 3일"));
  }

  @Test
  @DisplayName("startDate가 없으면 복제 요청은 400을 반환한다")
  void cloneToTravelRequiresStartDate() throws Exception {
    mockMvc
        .perform(
            post("/travel-records/{travelRecordId}/clone-to-travel", RECORD_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "부산 3일"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("발행된 기록은 인증 없이도 조회할 수 있다")
  void getPublishedIsPubliclyAccessible() throws Exception {
    when(travelRecordService.getPublished(any(), eq(RECORD_ID)))
        .thenReturn(record(TravelRecordStatus.PUBLISHED));

    mockMvc
        .perform(get("/travel-records/{travelRecordId}", RECORD_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLISHED"));
  }

  @Test
  @DisplayName("인증이 필요한 엔드포인트는 인증 없이 호출하면 UnauthenticatedException을 던진다")
  void rejectsUnauthenticatedUser() {
    assertThatThrownBy(() -> publicTravelRecordController.getMyRecords(null))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> publicTravelRecordController.getMyBookmarkedRecords(null))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> publicTravelRecordController.getMyRecord(null, RECORD_ID))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> publicTravelRecordController.updateMyRecord(null, RECORD_ID, null))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> publicTravelRecordController.hideMyRecord(null, RECORD_ID))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> publicTravelRecordController.republishMyRecord(null, RECORD_ID))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> publicTravelRecordController.bookmarkTravelRecord(null, RECORD_ID))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> publicTravelRecordController.unbookmarkTravelRecord(null, RECORD_ID))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> publicTravelRecordController.likeTravelRecord(null, RECORD_ID))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> publicTravelRecordController.unlikeTravelRecord(null, RECORD_ID))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> publicTravelRecordController.createComment(null, RECORD_ID, null))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(
            () -> publicTravelRecordController.updateComment(null, RECORD_ID, COMMENT_ID, null))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(
            () -> publicTravelRecordController.deleteComment(null, RECORD_ID, COMMENT_ID))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> publicTravelRecordController.cloneToTravel(null, RECORD_ID, null))
        .isInstanceOf(UnauthenticatedException.class);
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

  private TravelRecordBookmarkResDto bookmark() {
    return new TravelRecordBookmarkResDto(
        UUID.fromString("99999999-0000-0000-0000-000000000001"),
        LocalDateTime.of(2026, 9, 5, 14, 0),
        feedItem());
  }

  private TravelRecordCommentResDto comment(String content) {
    return new TravelRecordCommentResDto(
        COMMENT_ID,
        RECORD_ID,
        USER_ID,
        "댓글작성자",
        "https://cdn.example.com/profile.jpg",
        content,
        LocalDateTime.of(2026, 9, 5, 15, 0),
        LocalDateTime.of(2026, 9, 5, 15, 0));
  }

  private TravelRecordResDto record(TravelRecordStatus status) {
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
        LocalDateTime.of(2026, 9, 5, 12, 0),
        3L,
        10L,
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
