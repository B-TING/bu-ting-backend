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

import com.butingbe.domain.chat.dto.ChatroomResponse;
import com.butingbe.domain.chat.dto.ChatMessageResponse;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.chat.service.LocalChatroomService;
import com.butingbe.domain.user.entity.User; // 💡 준연님의 실제 User 엔티티 경로 확인
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class LocalChatroomControllerTest {

  private MockMvc mockMvc;

  @Mock
  private LocalChatroomService localChatroomService;

  @InjectMocks
  private LocalChatroomController localChatroomController;

  @BeforeEach
  void setUp(RestDocumentationContextProvider restDocumentation) {
    // @AuthenticationPrincipal 주입을 자동 해결해 줄 가짜 아규먼트 리졸버
    HandlerMethodArgumentResolver mockAuthResolver = new HandlerMethodArgumentResolver() {
      @Override
      public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
      }

      @Override
      public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                    NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return User.builder()
                .id(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
                .nickname("수영구보안관")
                .build();
      }
    };

    // 💡 2중 매핑을 유발하던 setCustomHandlerMapping 설정을 완전히 제거하여 404를 원천 차단합니다.
    mockMvc = MockMvcBuilders.standaloneSetup(localChatroomController)
            .setCustomArgumentResolvers(mockAuthResolver)
            .apply(documentationConfiguration(restDocumentation))
            .build();
  }

  private RequestMappingHandlerMapping apiPrefixHandlerMapping() {
    RequestMappingHandlerMapping handlerMapping = new RequestMappingHandlerMapping();
    handlerMapping.setPathPrefixes(
            Map.of(
                    "/chat/rooms",
                    HandlerTypePredicate.forAnnotation(RestController.class)
                            .and(HandlerTypePredicate.forBasePackage("com.butingbe.domain"))));
    return handlerMapping;
  }

  // ==========================================
  // 📍 GET ROOMS BY ZONE TEST
  // ==========================================

  @Test
  @DisplayName("지역 이름을 정확히 보내면 해당 권역의 채팅방 목록을 성공적으로 반환한다")
  void getRoomsByZoneSuccess() throws Exception {
    String zoneParam = "SUYEONG_NAMGU";
    ChatroomResponse mockRoomResponse = new ChatroomResponse(
            UUID.fromString("110e8400-e29b-41d4-a716-446655440000"),
            "수영구 오픈채팅방",
            "설명",
            "SUYEONG_NAMGU",
            30,
            1
    );
    List<ChatroomResponse> mockResponses = List.of(mockRoomResponse);
    when(localChatroomService.getRoomsByZone(ChatZone.SUYEONG_NAMGU)).thenReturn(mockResponses);

    mockMvc.perform(get("/chat/rooms/zone")
                    .param("zone", zoneParam)
                    .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andDo(document(
                    "chatroom-get-by-zone",
                    queryParameters(parameterWithName("zone").description("조회할 채팅 권역명 (예: SUYEONG_NAMGU)"))
            ));
  }

  // ==========================================
  // 📍 EXIT ROOM TEST
  // ==========================================

  @Test
  @DisplayName("채팅방 나가기 요청 시 서비스 로직을 태우고 200 OK를 반환한다")
  void exitRoomSuccess() throws Exception {
    UUID roomId = UUID.fromString("220e8400-e29b-41d4-a716-446655440000");

    doNothing().when(localChatroomService).exitChatroom(eq(roomId), any(UUID.class));

    mockMvc.perform(delete("/chat/rooms/{roomId}/exit", roomId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer opaque-token")
                    .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andDo(document(
                    "chatroom-exit",
                    pathParameters(parameterWithName("roomId").description("나가기할 채팅방 ID"))
            ));
  }

  // ==========================================
  // 📍 ENTER ROOM TEST
  // ==========================================

  @Test
  @DisplayName("채팅방 입장 시 과거 대화 내역 목록을 정확히 받아와 반환한다")
  void enterRoomSuccess() throws Exception {
    UUID roomId = UUID.fromString("220e8400-e29b-41d4-a716-446655440000");

    ChatMessageResponse mockMessageResponse = new ChatMessageResponse(
            UUID.randomUUID(),
            roomId,
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
            "수영구보안관",
            "안녕하세요",
            OffsetDateTime.now(),
            true
    );
    List<ChatMessageResponse> mockHistory = List.of(mockMessageResponse);

    when(localChatroomService.enterChatRoom(eq(roomId), any(UUID.class))).thenReturn(mockHistory);

    mockMvc.perform(post("/chat/rooms/{roomId}/enter", roomId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer opaque-token")
                    .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].content").value("안녕하세요"))
            .andDo(document(
                    "chatroom-enter",
                    pathParameters(parameterWithName("roomId").description("입장할 채팅방 ID")),
                    responseFields(
                            fieldWithPath("[].id").description("메시지 ID"),
                            fieldWithPath("[].roomId").description("채팅방 ID"),
                            fieldWithPath("[].userId").description("발신자 ID"),
                            fieldWithPath("[].senderNickname").description("발신자 닉네임"),
                            fieldWithPath("[].content").description("메시지 내용"),
                            fieldWithPath("[].createdAt").description("작성 시간"),
                            fieldWithPath("[].isMine").description("본인 작성 여부")
                    )
            ));
  }
}