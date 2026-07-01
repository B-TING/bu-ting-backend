package com.butingbe.global.config;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.service.OpaqueTokenService;
import com.butingbe.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Map;
import java.util.Optional;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // 기존 JwtTokenProvider 대신 현재 프로젝트의 OpaqueTokenService 주입
    private final OpaqueTokenService opaqueTokenService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/sub");
        registry.setApplicationDestinationPrefixes("/pub");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-stomp").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;

                // 💡 1. CONNECT 시점에 토큰 검사
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String bearerToken = accessor.getFirstNativeHeader("Authorization");
                    if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
                        String rawToken = bearerToken.substring(7).trim();

                        opaqueTokenService.authenticate(rawToken).ifPresent(user -> {
                            // 세션 속성에 유저 정보 저장 (컨트롤러에서 꺼내 쓰기용)
                            if (accessor.getSessionAttributes() != null) {
                                accessor.getSessionAttributes().put("LOGIN_USER", AuthenticatedUser.from(user));
                            }
                        });
                    }
                }
                // 💡 2. 고민하지 말고 무조건 원본 메시지를 그대로 통과시킵니다 (연결 끊김 방지)
                return message;
            }
        });
    }
}
