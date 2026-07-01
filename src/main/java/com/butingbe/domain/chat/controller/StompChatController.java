package com.butingbe.domain.chat.controller;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.service.OpaqueTokenService;
import com.butingbe.domain.chat.dto.ChatMessageRequest;
import com.butingbe.domain.chat.entity.ChatMessage;
import com.butingbe.domain.chat.repository.ChatMessageRepository;
import com.butingbe.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class StompChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository chatMessageRepository;
    private final OpaqueTokenService opaqueTokenService;

    @MessageMapping("/chat/message")
    public void handleMessage(ChatMessageRequest dto, SimpMessageHeaderAccessor accessor) {
        // 1. 헤더에서 토큰 추출
        String bearerToken = accessor.getFirstNativeHeader("Authorization");

        // 2. 토큰 검증 및 유저 정보 획득 (유효하지 않으면 예외 발생)
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new IllegalStateException("인증 토큰이 누락되었거나 형식이 잘못되었습니다.");
        }

        String rawToken = bearerToken.substring(7).trim();

        // 인증 성공 시에만 로직 진행
        User user = opaqueTokenService.authenticate(rawToken)
                .orElseThrow(() -> new IllegalStateException("유효하지 않은 토큰입니다. 인증에 실패했습니다."));

        // 3. 정상적인 유저 정보로 채팅 저장 및 브로드캐스팅
        ChatMessage chatMessage = ChatMessage.builder()
                .roomId(dto.roomId())
                .userId(user.getId())
                .senderNickname(user.getNickname())
                .content(dto.content())
                .build();

        chatMessageRepository.save(chatMessage);
        messagingTemplate.convertAndSend("/sub/chat/room/" + dto.roomId(), chatMessage);
    }
}