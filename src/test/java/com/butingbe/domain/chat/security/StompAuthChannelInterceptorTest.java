package com.butingbe.domain.chat.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.service.OpaqueTokenService;
import com.butingbe.domain.chat.repository.ChatMemberRepository;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

  private static final UUID ROOM_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("22222222-0000-0000-0000-000000000001");

  @Mock private OpaqueTokenService opaqueTokenService;
  @Mock private ChatMemberRepository chatMemberRepository;

  @InjectMocks private StompAuthChannelInterceptor interceptor;

  @Test
  @DisplayName("STOMP 헤더가 없는 메시지는 그대로 통과시킨다")
  void passesThroughNonStompMessage() {
    Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).build();

    assertThat(interceptor.preSend(message, null)).isSameAs(message);
  }

  @Test
  @DisplayName("유효한 토큰으로 CONNECT하면 세션에 로그인 사용자를 저장한다")
  void connectStoresAuthenticatedUser() {
    when(opaqueTokenService.authenticate("valid-token")).thenReturn(Optional.of(user()));

    StompHeaderAccessor accessor = accessor(StompCommand.CONNECT);
    accessor.setNativeHeader("Authorization", "Bearer valid-token");
    Map<String, Object> sessionAttributes = new HashMap<>();
    accessor.setSessionAttributes(sessionAttributes);

    interceptor.preSend(message(accessor), null);

    AuthenticatedUser stored =
        (AuthenticatedUser) sessionAttributes.get(StompAuthChannelInterceptor.LOGIN_USER_ATTRIBUTE);
    assertThat(stored.id()).isEqualTo(USER_ID);
    assertThat(stored.nickname()).isEqualTo("tester");
  }

  @Test
  @DisplayName("토큰 없이 CONNECT하면 연결을 거부한다")
  void connectWithoutTokenIsRejected() {
    StompHeaderAccessor accessor = accessor(StompCommand.CONNECT);
    Message<byte[]> message = message(accessor);

    assertThatThrownBy(() -> interceptor.preSend(message, null))
        .isInstanceOf(MessageDeliveryException.class)
        .hasMessageContaining("인증 토큰이 필요합니다");
  }

  @Test
  @DisplayName("잘못된 토큰으로 CONNECT하면 연결을 거부한다")
  void connectWithInvalidTokenIsRejected() {
    when(opaqueTokenService.authenticate("broken-token")).thenReturn(Optional.empty());

    StompHeaderAccessor accessor = accessor(StompCommand.CONNECT);
    accessor.setNativeHeader("Authorization", "Bearer broken-token");
    Message<byte[]> message = message(accessor);

    assertThatThrownBy(() -> interceptor.preSend(message, null))
        .isInstanceOf(MessageDeliveryException.class)
        .hasMessageContaining("유효하지 않은 인증 토큰");
  }

  @Test
  @DisplayName("참여 중인 방은 구독할 수 있다")
  void subscribeToJoinedRoomIsAllowed() {
    when(chatMemberRepository.existsByIdRoomIdAndIdUserId(ROOM_ID, USER_ID)).thenReturn(true);

    Message<byte[]> message = subscribeMessage("/sub/chat/room/" + ROOM_ID, authenticatedUser());

    assertThat(interceptor.preSend(message, null)).isSameAs(message);
  }

  @Test
  @DisplayName("참여하지 않은 방 구독은 거부한다")
  void subscribeToOtherRoomIsRejected() {
    when(chatMemberRepository.existsByIdRoomIdAndIdUserId(ROOM_ID, USER_ID)).thenReturn(false);

    Message<byte[]> message = subscribeMessage("/sub/chat/room/" + ROOM_ID, authenticatedUser());

    assertThatThrownBy(() -> interceptor.preSend(message, null))
        .isInstanceOf(MessageDeliveryException.class)
        .hasMessageContaining("참여하지 않은 채팅방");
  }

  @Test
  @DisplayName("인증 정보가 없는 세션의 채팅방 구독은 거부한다")
  void subscribeWithoutLoginUserIsRejected() {
    Message<byte[]> message = subscribeMessage("/sub/chat/room/" + ROOM_ID, null);

    assertThatThrownBy(() -> interceptor.preSend(message, null))
        .isInstanceOf(MessageDeliveryException.class)
        .hasMessageContaining("인증되지 않은 사용자");
  }

  @Test
  @DisplayName("세션 속성이 아예 없는 채팅방 구독도 거부한다")
  void subscribeWithoutSessionAttributesIsRejected() {
    StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE);
    accessor.setDestination("/sub/chat/room/" + ROOM_ID);
    Message<byte[]> message = message(accessor);

    assertThatThrownBy(() -> interceptor.preSend(message, null))
        .isInstanceOf(MessageDeliveryException.class)
        .hasMessageContaining("인증되지 않은 사용자");
  }

  @Test
  @DisplayName("채팅방이 아닌 목적지 구독은 검사하지 않는다")
  void subscribeToOtherDestinationIsUntouched() {
    StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE);
    accessor.setDestination("/sub/notice");
    Message<byte[]> message = message(accessor);

    assertThat(interceptor.preSend(message, null)).isSameAs(message);
  }

  @Test
  @DisplayName("목적지가 없는 구독은 검사하지 않는다")
  void subscribeWithoutDestinationIsUntouched() {
    Message<byte[]> message = message(accessor(StompCommand.SUBSCRIBE));

    assertThat(interceptor.preSend(message, null)).isSameAs(message);
  }

  private Message<byte[]> subscribeMessage(String destination, AuthenticatedUser user) {
    StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE);
    accessor.setDestination(destination);
    Map<String, Object> sessionAttributes = new HashMap<>();
    if (user != null) {
      sessionAttributes.put(StompAuthChannelInterceptor.LOGIN_USER_ATTRIBUTE, user);
    }
    accessor.setSessionAttributes(sessionAttributes);
    return message(accessor);
  }

  private StompHeaderAccessor accessor(StompCommand command) {
    return StompHeaderAccessor.create(command);
  }

  private Message<byte[]> message(StompHeaderAccessor accessor) {
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }

  private AuthenticatedUser authenticatedUser() {
    return new AuthenticatedUser(USER_ID, "user@example.com", "tester", List.of());
  }

  private User user() {
    return User.builder()
        .id(USER_ID)
        .email("user@example.com")
        .nickname("tester")
        .role(UserRole.USER)
        .build();
  }
}
