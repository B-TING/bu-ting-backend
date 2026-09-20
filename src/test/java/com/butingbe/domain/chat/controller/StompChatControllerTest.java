package com.butingbe.domain.chat.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.chat.dto.ChatMessageRequest;
import com.butingbe.domain.chat.security.StompAuthChannelInterceptor;
import com.butingbe.domain.chat.service.LocalChatroomService;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

@ExtendWith(MockitoExtension.class)
class StompChatControllerTest {

  private static final UUID ROOM_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private LocalChatroomService localChatroomService;

  @InjectMocks private StompChatController stompChatController;

  @Test
  @DisplayName("인증된 사용자의 메시지는 서비스로 위임된다")
  void handleMessageDelegatesToService() {
    AuthenticatedUser user = authenticatedUser();

    stompChatController.handleMessage(
        new ChatMessageRequest(ROOM_ID, "안녕하세요"), accessorWithUser(user));

    verify(localChatroomService).sendMessage(ROOM_ID, user, "안녕하세요");
  }

  @Test
  @DisplayName("세션 속성이 없으면 인증 예외를 던진다")
  void handleMessageRejectsMissingSessionAttributes() {
    SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();

    assertThatThrownBy(
            () ->
                stompChatController.handleMessage(
                    new ChatMessageRequest(ROOM_ID, "안녕하세요"), accessor))
        .isInstanceOf(UnauthenticatedException.class);
  }

  @Test
  @DisplayName("세션에 로그인 사용자가 없으면 인증 예외를 던진다")
  void handleMessageRejectsUnauthenticatedSession() {
    assertThatThrownBy(
            () ->
                stompChatController.handleMessage(
                    new ChatMessageRequest(ROOM_ID, "안녕하세요"), accessorWithUser(null)))
        .isInstanceOf(UnauthenticatedException.class);
  }

  private SimpMessageHeaderAccessor accessorWithUser(AuthenticatedUser user) {
    SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
    Map<String, Object> sessionAttributes = new HashMap<>();
    if (user != null) {
      sessionAttributes.put(StompAuthChannelInterceptor.LOGIN_USER_ATTRIBUTE, user);
    }
    accessor.setSessionAttributes(sessionAttributes);
    return accessor;
  }

  private AuthenticatedUser authenticatedUser() {
    return new AuthenticatedUser(USER_ID, "user@example.com", "tester", List.of());
  }
}
