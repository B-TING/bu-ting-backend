package com.butingbe.domain.chat.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ChatMessageTest {

    @Test
    @DisplayName("ChatMessage 빌더 생성자 및 자동 시간 초기화 로직 커버리지 검증")
    void chatMessageBuilderAndInitializationTest() {
        // Given
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String senderNickname = "수영구보안관";
        String content = "안녕하세요! 오늘 대화방 오픈했습니다.";

        // When
        // 💡 엔티티 내부의 @Builder 생성자 라인을 직접 실행 (JaCoCo 커버리지 충족)
        ChatMessage chatMessage = ChatMessage.builder()
                .roomId(roomId)
                .userId(userId)
                .senderNickname(senderNickname)
                .content(content)
                .build();

        // Then
        // 각 필드의 Getter를 호출하여 롬복 커버리지를 채우고, 올바른 값이 들어갔는지 검증
        assertThat(chatMessage.getRoomId()).isEqualTo(roomId);
        assertThat(chatMessage.getUserId()).isEqualTo(userId);
        assertThat(chatMessage.getSenderNickname()).isEqualTo(senderNickname);
        assertThat(chatMessage.getContent()).isEqualTo(content);

        // 💡 중요: 생성자 내부에서 OffsetDateTime.now() 로직이 실행되었는지 검증 (JaCoCo 라인 커버리지 충족)
        assertThat(chatMessage.getCreatedAt()).isNotNull();

        // 아직 DB에 저장 전이므로 @GeneratedValue가 붙은 messageId는 null이어야 함
        assertThat(chatMessage.getMessageId()).isNull();
    }
}