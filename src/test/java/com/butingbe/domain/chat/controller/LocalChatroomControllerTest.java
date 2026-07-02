package com.butingbe.domain.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.butingbe.domain.chat.dto.ChatMessageResponse;
import com.butingbe.domain.chat.dto.ChatroomResponse;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.chat.service.LocalChatroomService;
import com.butingbe.domain.user.entity.User;
import java.time.OffsetDateTime;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class LocalChatroomControllerTest {

  private MockMvc mockMvc;

  @Mock private LocalChatroomService localChatroomService;

  @InjectMocks private LocalChatroomController localChatroomController;

  @BeforeEach
  void setUp(RestDocumentationContextProvider restDocumentation) {
    // 💡 @AuthenticationPrincipal User loginUser 자리에 가짜 유저 객체를 주입해 줄 리졸버
    HandlerMethodArgumentResolver mockAuthResolver =
        new HandlerMethodArgumentResolver() {
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
            return User.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
                .nickname("수영구보안관")
                .build();
          }
        };

    // 💡 404를 일으키던 커스텀 패스 프리픽스 설정을 빼고 순수하게 빌드합니다.
    mockMvc =
        MockMvcBuilders.standaloneSetup(localChatroomController)
            .setCustomArgumentResolvers(mockAuthResolver)
            .apply(documentationConfiguration(restDocumentation))
            .build();
  }

  // ==========================================
  // 📍 GET ROOMS BY ZONE TEST (지역별 조회)
  // ==========================================

  @Test
  @DisplayName("지역 이름을 쿼리 파라미터로 보내면 ApiResponse 규격에 맞춰 방 목록을 반환한다")
  void getRoomsByZoneSuccess() throws Exception {
    // given
    String zoneParam = "SUYEONG_NAMGU";
    ChatroomResponse mockRoomResponse =
        new ChatroomResponse(
            UUID.fromString("110e8400-e29b-41d4-a716-446655440000"),
            "수영구 오픈채팅방",
            "설명",
            "SUYEONG_NAMGU",
            30,
            1);
    when(localChatroomService.getRoomsByZone(any(ChatZone.class)))
        .thenReturn(List.of(mockRoomResponse));

    // when & then
    mockMvc
        .perform(
            get("/chat/rooms/zone")
                .param("zone", zoneParam)
                .contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true)) // 💡 Boolean 타입이므로 따옴표 없이 true를 적어줍니다.
        .andExpect(jsonPath("$.message").value("지역별 채팅방 조회"))
        .andExpect(jsonPath("$.data[0].title").value("수영구 오픈채팅방"))
        .andDo(
            document(
                "chatroom-get-by-zone",
                queryParameters(
                    parameterWithName("zone").description("조회할 채팅 권역명 (예: SUYEONG_NAMGU)")),
                responseFields(
                    fieldWithPath("success")
                        .description("응답 성공 여부 (true/false)"), // 💡 status -> success 수정
                    fieldWithPath("message").description("응답 메시지"),

                    // 💡 실제 응답 데이터의 필드명인 'roomId'로 정밀 조준합니다!
                    fieldWithPath("data[].roomId").description("채팅방 ID"),
                    fieldWithPath("data[].title").description("채팅방 이름"),
                    fieldWithPath("data[].description").description("채팅방 설명"),
                    fieldWithPath("data[].chatZone").description("채팅 권역"),
                    fieldWithPath("data[].maxMembers").description("최대 정원"),
                    fieldWithPath("data[].currentMembers").description("현재 인원"))));
  }

  // ==========================================
  // 📍 EXIT ROOM TEST (방 나가기)
  // ==========================================

  @Test
  @DisplayName("인증된 사용자가 채팅방 나가기 요청을 하면 ApiResponse 규격으로 응답한다")
  void exitRoomSuccess() throws Exception {
    // given
    UUID roomId = UUID.fromString("220e8400-e29b-41d4-a716-446655440000");
    doNothing().when(localChatroomService).exitChatroom(eq(roomId), any(UUID.class));

    // when & then
    mockMvc
        .perform(
            delete("/chat/rooms/{roomId}/exit", roomId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer opaque-token")
                .contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true)) // 💡 여기도 동일하게 변경!
        .andExpect(jsonPath("$.message").value("채팅방 나가기 완료"))
        .andDo(
            document(
                "chatroom-exit",
                pathParameters(parameterWithName("roomId").description("나가기할 채팅방 ID")),
                responseFields(
                    fieldWithPath("success")
                        .description("응답 성공 여부 (true/false)"), // 💡 status 대신 success로 변경!
                    fieldWithPath("message").description("응답 메시지"),
                    fieldWithPath("data").description("응답 데이터"))));
  }

  // ==========================================
  // 📍 ENTER ROOM TEST (방 입장 및 과거 내역)
  // ==========================================

  @Test
  @DisplayName("채팅방 입장 시 ApiResponse로 감싸지 않은 순수 JSON 배열 형태로 과거 메시지 내역을 반환한다")
  void enterRoomSuccess() throws Exception {
    // given
    UUID roomId = UUID.fromString("220e8400-e29b-41d4-a716-446655440000");
    ChatMessageResponse mockMessageResponse =
        new ChatMessageResponse(
            UUID.randomUUID(),
            roomId,
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
            "수영구보안관",
            "안녕하세요",
            OffsetDateTime.now(),
            true);
    when(localChatroomService.enterChatRoom(eq(roomId), any(UUID.class)))
        .thenReturn(List.of(mockMessageResponse));

    // when & then
    mockMvc
        .perform(
            post("/chat/rooms/{roomId}/enter", roomId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer opaque-token")
                .contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isOk())
        // 💡 이 메서드는 ApiResponse가 아니라 순수 List 반환이므로 루트($)가 배열([])인지 체크합니다.
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].content").value("안녕하세요"))
        .andDo(
            document(
                "chatroom-enter",
                pathParameters(parameterWithName("roomId").description("입장할 채팅방 ID")),
                responseFields(
                    fieldWithPath("[].id").description("메시지 ID"),
                    fieldWithPath("[].roomId").description("채팅방 ID"),
                    fieldWithPath("[].userId").description("발신자 ID"),
                    fieldWithPath("[].senderNickname").description("발신자 닉네임"),
                    fieldWithPath("[].content").description("메시지 내용"),
                    fieldWithPath("[].createdAt").description("작성 시간"),
                    fieldWithPath("[].isMine").description("본인 작성 여부"))));
  }
}
