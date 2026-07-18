package com.butingbe.domain.chat.listener;

import com.butingbe.domain.chat.service.LocalChatroomService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

  private final LocalChatroomService localChatroomService;

  // 💡 1. 사용자가 채팅방 채널을 구독(입장)했을 때
  @EventListener
  public void handleSubscribe(SessionSubscribeEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    String destination = accessor.getDestination();

    if (destination != null
        && destination.startsWith("/sub/chat/room/")
        && !destination.endsWith("/status")) {
      // 주소에서 roomId 추출 (/sub/chat/room/{roomId})
      String roomIdStr = destination.substring("/sub/chat/room/".length()).split("/")[0];

      UUID roomId = UUID.fromString(roomIdStr);

      // 세션에 현재 방 ID 저장 (나중에 disconnect 때 쓰기 위함)
      if (accessor.getSessionAttributes() != null) {
        accessor.getSessionAttributes().put("CURRENT_ROOM_ID", roomIdStr);
      }

      // 실시간 인원수 +1
      localChatroomService.enterLiveChatroom(roomId);
    }
  }

  // 💡 2. 웹소켓 연결이 끊겼을 때 (뒤로가기, 앱 강제종료 모두 포함)
  @EventListener
  public void handleDisconnect(SessionDisconnectEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

    if (accessor.getSessionAttributes() == null) return;
    String roomIdStr = (String) accessor.getSessionAttributes().get("CURRENT_ROOM_ID");

    if (roomIdStr != null) {
      UUID roomId = UUID.fromString(roomIdStr);

      // 앱을 껐거나 나갔으므로 실시간 인원수 -1
      localChatroomService.exitLiveChatroom(roomId);
    }
  }
}
