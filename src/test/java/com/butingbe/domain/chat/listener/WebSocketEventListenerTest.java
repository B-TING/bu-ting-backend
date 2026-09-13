package com.butingbe.domain.chat.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.butingbe.domain.chat.service.LocalChatroomService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@ExtendWith(MockitoExtension.class)
class WebSocketEventListenerTest {

  private static final UUID ROOM_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

  @Mock private LocalChatroomService localChatroomService;

  @InjectMocks private WebSocketEventListener webSocketEventListener;

  @Test
  @DisplayName("채팅방을 구독하면 실시간 인원이 증가하고 세션에 방 ID가 저장된다")
  void handleSubscribeEntersLiveChatroom() {
    Map<String, Object> sessionAttributes = new HashMap<>();
    Message<byte[]> message = subscribeMessage("/sub/chat/room/" + ROOM_ID, sessionAttributes);

    webSocketEventListener.handleSubscribe(new SessionSubscribeEvent(this, message));

    verify(localChatroomService).enterLiveChatroom(ROOM_ID);
    assertThat(sessionAttributes).containsEntry("CURRENT_ROOM_ID", ROOM_ID.toString());
  }

  @Test
  @DisplayName("status 채널 구독은 실시간 인원에 반영하지 않는다")
  void handleSubscribeIgnoresStatusDestination() {
    Message<byte[]> message =
        subscribeMessage("/sub/chat/room/" + ROOM_ID + "/status", new HashMap<>());

    webSocketEventListener.handleSubscribe(new SessionSubscribeEvent(this, message));

    verifyNoInteractions(localChatroomService);
  }

  @Test
  @DisplayName("채팅방과 무관한 채널 구독은 무시한다")
  void handleSubscribeIgnoresUnrelatedDestination() {
    Message<byte[]> message = subscribeMessage("/sub/notice", new HashMap<>());

    webSocketEventListener.handleSubscribe(new SessionSubscribeEvent(this, message));

    verifyNoInteractions(localChatroomService);
  }

  @Test
  @DisplayName("연결이 끊기면 세션에 저장된 방의 실시간 인원이 감소한다")
  void handleDisconnectExitsLiveChatroom() {
    Map<String, Object> sessionAttributes = new HashMap<>();
    sessionAttributes.put("CURRENT_ROOM_ID", ROOM_ID.toString());

    webSocketEventListener.handleDisconnect(disconnectEvent(sessionAttributes));

    verify(localChatroomService).exitLiveChatroom(ROOM_ID);
  }

  @Test
  @DisplayName("세션에 방 ID가 없으면 연결 종료 시 아무 것도 하지 않는다")
  void handleDisconnectWithoutRoomIdDoesNothing() {
    webSocketEventListener.handleDisconnect(disconnectEvent(new HashMap<>()));

    verifyNoInteractions(localChatroomService);
  }

  private Message<byte[]> subscribeMessage(
      String destination, Map<String, Object> sessionAttributes) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    accessor.setDestination(destination);
    accessor.setSessionAttributes(sessionAttributes);
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }

  private SessionDisconnectEvent disconnectEvent(Map<String, Object> sessionAttributes) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
    accessor.setSessionId("session-1");
    accessor.setSessionAttributes(sessionAttributes);
    accessor.setLeaveMutable(true);
    Message<byte[]> message =
        MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    return new SessionDisconnectEvent(
        this, message, "session-1", org.springframework.web.socket.CloseStatus.NORMAL);
  }
}
