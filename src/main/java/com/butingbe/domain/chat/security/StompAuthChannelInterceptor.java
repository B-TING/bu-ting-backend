package com.butingbe.domain.chat.security;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.service.OpaqueTokenService;
import com.butingbe.domain.chat.repository.ChatMemberRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP CONNECT는 유효한 액세스 토큰을 요구하고, 채팅방 구독은 그 방의 참여자만 허용한다.
 *
 * <p>브로커로 메시지가 넘어가기 전에 막아야 하므로 인바운드 채널 인터셉터에서 검사한다.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

  public static final String LOGIN_USER_ATTRIBUTE = "LOGIN_USER";

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String ROOM_DESTINATION_PREFIX = "/sub/chat/room/";

  private final OpaqueTokenService opaqueTokenService;
  private final ChatMemberRepository chatMemberRepository;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null) {
      return message;
    }

    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
      authenticate(message, accessor);
    } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
      verifySubscription(message, accessor);
    }
    return message;
  }

  private void authenticate(Message<?> message, StompHeaderAccessor accessor) {
    String bearerToken = accessor.getFirstNativeHeader("Authorization");
    if (bearerToken == null || !bearerToken.startsWith(BEARER_PREFIX)) {
      throw new MessageDeliveryException(message, "WebSocket 연결에는 인증 토큰이 필요합니다.");
    }

    AuthenticatedUser user =
        opaqueTokenService
            .authenticate(bearerToken.substring(BEARER_PREFIX.length()).trim())
            .map(AuthenticatedUser::from)
            .orElseThrow(() -> new MessageDeliveryException(message, "유효하지 않은 인증 토큰입니다."));

    Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
    if (sessionAttributes != null) {
      sessionAttributes.put(LOGIN_USER_ATTRIBUTE, user);
    }
  }

  private void verifySubscription(Message<?> message, StompHeaderAccessor accessor) {
    String destination = accessor.getDestination();
    if (destination == null || !destination.startsWith(ROOM_DESTINATION_PREFIX)) {
      return;
    }

    UUID roomId =
        UUID.fromString(destination.substring(ROOM_DESTINATION_PREFIX.length()).split("/")[0]);
    AuthenticatedUser user = loginUser(accessor);
    if (user == null) {
      throw new MessageDeliveryException(message, "인증되지 않은 사용자입니다.");
    }
    if (!chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, user.id())) {
      throw new MessageDeliveryException(message, "참여하지 않은 채팅방은 구독할 수 없습니다.");
    }
  }

  private AuthenticatedUser loginUser(StompHeaderAccessor accessor) {
    Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
    if (sessionAttributes == null) {
      return null;
    }
    return (AuthenticatedUser) sessionAttributes.get(LOGIN_USER_ATTRIBUTE);
  }
}
