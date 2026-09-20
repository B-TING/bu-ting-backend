package com.butingbe.domain.chat.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.chat.dto.ChatMessageRequest;
import com.butingbe.domain.chat.security.StompAuthChannelInterceptor;
import com.butingbe.domain.chat.service.LocalChatroomService;
import com.butingbe.global.error.exception.UnauthenticatedException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class StompChatController {

  private final LocalChatroomService localChatroomService;

  @MessageMapping("/chat/message")
  public void handleMessage(@Payload ChatMessageRequest dto, SimpMessageHeaderAccessor accessor) {
    localChatroomService.sendMessage(dto.roomId(), loginUser(accessor), dto.content());
  }

  private AuthenticatedUser loginUser(SimpMessageHeaderAccessor accessor) {
    if (accessor.getSessionAttributes() == null) {
      throw new UnauthenticatedException();
    }

    AuthenticatedUser user =
        (AuthenticatedUser)
            accessor.getSessionAttributes().get(StompAuthChannelInterceptor.LOGIN_USER_ATTRIBUTE);
    if (user == null) {
      throw new UnauthenticatedException();
    }
    return user;
  }
}
