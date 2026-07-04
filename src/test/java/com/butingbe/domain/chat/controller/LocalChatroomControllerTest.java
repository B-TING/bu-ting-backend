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

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.chat.dto.ChatroomResponse;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.chat.service.LocalChatroomService;
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

  // 🎯 준연님이 작성하신 완벽한 오리지널 setUp 코드 그대로 박아두었습니다.
  @BeforeEach
  void setUp(RestDocumentationContextProvider restDocumentation) {
    // 💡 @AuthenticationPrincipal AuthenticatedUser 자리에 가짜 객체를 주입해 줄 리졸버
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

            // ⚠️ [수정] 제공해주신 AuthenticatedUser record 구조에 맞게 가짜 객체 생성하여 반환
            return new AuthenticatedUser(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                "test@example.com",
                "수영구보안관",
                java.util.List.of(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_USER")));
          }
        };

    // 💡 순수하게 빌드 - JSON 메세지 컨버터 장착 완료
    mockMvc =
        MockMvcBuilders.standaloneSetup(localChatroomController)
            .setCustomArgumentResolvers(mockAuthResolver)
            .setMessageConverters(
                new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter())
            .apply(documentationConfiguration(restDocumentation))
            .build();
  }

  // ==========================================
  // 📍 GET ROOMS BY ZONE TEST (지역별 조회)
  // ==========================================

  @Test
  @DisplayName("지역 이름을 쿼리 파라미터로 보내면 success 규격에 맞춰 방 목록을 반환한다")
  void getRoomsByZoneSuccess() throws Exception {
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

    mockMvc
        .perform(
            get("/chat/rooms/zone")
                .param("zone", zoneParam)
                .contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("지역별 채팅방 조회"))
        .andExpect(jsonPath("$.data[0].roomId").value("110e8400-e29b-41d4-a716-446655440000"))
        .andDo(
            document(
                "chatroom-get-by-zone",
                queryParameters(
                    parameterWithName("zone").description("조회할 채팅 권역명 (예: SUYEONG_NAMGU)")),
                responseFields(
                    fieldWithPath("success").description("응답 성공 여부 (true/false)"),
                    fieldWithPath("message").description("응답 메시지"),
                    fieldWithPath("data[].roomId").description("채팅방 ID"),
                    fieldWithPath("data[].title").description("채팅방 이름"),
                    fieldWithPath("data[].description").description("채팅방 설명"),
                    fieldWithPath("data[].chatZone").description("채팅 권역"),
                    fieldWithPath("data[].maxMembers").description("최대 정원"),
                    fieldWithPath("data[].currentMembers").description("현재 인원"))));
  }

  // ==========================================
  // 📍 JOIN CHATROOM TEST (방 가입)
  // ==========================================

  @Test
  @DisplayName("인증된 사용자가 오픈채팅방 가입 요청을 하면 성공 응답 규격으로 응답한다")
  void joinChatroomSuccess() throws Exception {
    UUID roomId = UUID.fromString("220e8400-e29b-41d4-a716-446655440000");
    doNothing().when(localChatroomService).joinRoom(eq(roomId), any(UUID.class));

    mockMvc
        .perform(
            post("/chat/rooms/{roomId}/join", roomId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer opaque-token")
                .contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("채팅방 가입 완료"))
        .andDo(
            document(
                "chatroom-join",
                pathParameters(parameterWithName("roomId").description("가입할 채팅방 ID")),
                responseFields(
                    fieldWithPath("success").description("응답 성공 여부"),
                    fieldWithPath("message").description("응답 메시지"),
                    fieldWithPath("data").description("응답 데이터 (null)"))));
  }

  // ==========================================
  // 📍 EXIT ROOM TEST (방 나가기)
  // ==========================================

  @Test
  @DisplayName("인증된 사용자가 오픈채팅방 나가기 요청을 하면 성공 응답을 반환한다")
  void exitRoomSuccess() throws Exception {
    UUID roomId = UUID.fromString("220e8400-e29b-41d4-a716-446655440000");
    doNothing().when(localChatroomService).exitChatroom(eq(roomId), any(UUID.class));

    mockMvc
        .perform(
            delete("/chat/rooms/{roomId}/exit", roomId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer opaque-token")
                .contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("채팅방 나가기 완료"))
        .andDo(
            document(
                "chatroom-exit",
                pathParameters(parameterWithName("roomId").description("나가기할 채팅방 ID")),
                responseFields(
                    fieldWithPath("success").description("응답 성공 여부"),
                    fieldWithPath("message").description("응답 메시지"),
                    fieldWithPath("data").description("응답 데이터 (null)"))));
  }
}
