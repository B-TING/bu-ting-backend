package com.butingbe.domain.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.chat.dto.ChatMessageRequest;
import com.butingbe.domain.chat.dto.ChatMessageResponse;
import com.butingbe.domain.chat.entity.ChatMessage;
import com.butingbe.domain.chat.repository.ChatMessageRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class StompChatControllerTest {

  private static final UUID ROOM_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");
  @Mock private SimpMessagingTemplate messagingTemplate;

  @Mock private ChatMessageRepository chatMessageRepository;

  @InjectMocks private StompChatController stompChatController;

  @Test
  @DisplayName("인증된 사용자의 메시지는 저장된 뒤 해당 방 구독자에게 발행된다")
  void handleMessagePersistsAndBroadcasts() {
    when(chatMessageRepository.save(any(ChatMessage.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    stompChatController.handleMessage(
        new ChatMessageRequest(ROOM_ID, "안녕하세요"), accessorWithUser(authenticatedUser()));

    ArgumentCaptor<ChatMessage> savedCaptor = ArgumentCaptor.forClass(ChatMessage.class);
    verify(chatMessageRepository).save(savedCaptor.capture());
    assertThat(savedCaptor.getValue().getRoomId()).isEqualTo(ROOM_ID);
    assertThat(savedCaptor.getValue().getUserId()).isEqualTo(USER_ID);
    assertThat(savedCaptor.getValue().getSenderNickname()).isEqualTo("tester");
    assertThat(savedCaptor.getValue().getContent()).isEqualTo("안녕하세요");

    ArgumentCaptor<ChatMessageResponse> publishedCaptor =
        ArgumentCaptor.forClass(ChatMessageResponse.class);
    verify(messagingTemplate)
        .convertAndSend(
            org.mockito.ArgumentMatchers.eq("/sub/chat/room/" + ROOM_ID),
            publishedCaptor.capture());
    assertThat(publishedCaptor.getValue().roomId()).isEqualTo(ROOM_ID);
    assertThat(publishedCaptor.getValue().senderId()).isEqualTo(USER_ID);
    assertThat(publishedCaptor.getValue().senderNickname()).isEqualTo("tester");
    assertThat(publishedCaptor.getValue().content()).isEqualTo("안녕하세요");
    assertThat(publishedCaptor.getValue().createdAt()).isNotNull();
    assertThat(publishedCaptor.getValue().isMine()).isNull();
  }

  @Test
  @DisplayName("세션 속성이 없으면 IllegalStateException을 던진다")
  void handleMessageRejectsMissingSessionAttributes() {
    SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();

    assertThatThrownBy(
            () ->
                stompChatController.handleMessage(
                    new ChatMessageRequest(ROOM_ID, "안녕하세요"), accessor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("세션 속성이 존재하지 않습니다.");
  }

  @Test
  @DisplayName("세션에 로그인 사용자가 없으면 IllegalStateException을 던진다")
  void handleMessageRejectsUnauthenticatedSession() {
    assertThatThrownBy(
            () ->
                stompChatController.handleMessage(
                    new ChatMessageRequest(ROOM_ID, "안녕하세요"), accessorWithUser(null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("인증되지 않은 사용자입니다.");
  }

  private SimpMessageHeaderAccessor accessorWithUser(AuthenticatedUser user) {
    SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
    Map<String, Object> sessionAttributes = new HashMap<>();
    if (user != null) {
      sessionAttributes.put("LOGIN_USER", user);
    }
    accessor.setSessionAttributes(sessionAttributes);
    return accessor;
  }

  private AuthenticatedUser authenticatedUser() {
    return new AuthenticatedUser(USER_ID, "user@example.com", "tester", List.of());
  }
}
