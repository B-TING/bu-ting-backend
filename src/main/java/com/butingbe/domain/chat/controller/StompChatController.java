package com.butingbe.domain.chat.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.chat.dto.ChatMessageRequest;
import com.butingbe.domain.chat.dto.ChatMessageResponse;
import com.butingbe.domain.chat.entity.ChatMessage;
import com.butingbe.domain.chat.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class StompChatController {

  private final SimpMessagingTemplate messagingTemplate;
  private final ChatMessageRepository chatMessageRepository;

  @MessageMapping("/chat/message")
  public void handleMessage(@Payload ChatMessageRequest dto, SimpMessageHeaderAccessor accessor) {

    if (accessor.getSessionAttributes() == null) {
      throw new IllegalStateException("세션 속성이 존재하지 않습니다.");
    }

    // 💡 Config에서 'AuthenticatedUser'로 넣었으므로 꺼낼 때도 똑같이 맞춰줍니다!
    AuthenticatedUser user = (AuthenticatedUser) accessor.getSessionAttributes().get("LOGIN_USER");
    if (user == null) {
      throw new IllegalStateException("인증되지 않은 사용자입니다.");
    }

    // record 형식이면 user.id(), 일반 클래스면 user.getId() 등 프로젝트 규격에 맞게 꺼내 쓰시면 됩니다.
    ChatMessage chatMessage =
        ChatMessage.builder()
            .roomId(dto.roomId())
            .userId(user.id())
            .senderNickname(user.nickname())
            .content(dto.content())
            .build();

    ChatMessage savedMessage = chatMessageRepository.save(chatMessage);
    messagingTemplate.convertAndSend(
        "/sub/chat/room/" + dto.roomId(), ChatMessageResponse.from(savedMessage, null));
  }
}
