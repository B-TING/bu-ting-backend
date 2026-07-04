package com.butingbe.domain.chat.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatMemberIdTest {

  @Test
  @DisplayName("ChatMemberId 생성자 및 Getter 로직 커버리지 검증")
  void chatMemberIdCreationAndGetterTest() {
    // Given
    UUID roomId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    // When
    // 💡 커스텀 생성자 직접 호출 (JaCoCo 커버리지 충족)
    ChatMemberId chatMemberId = new ChatMemberId(roomId, userId);

    // Then
    // 💡 Getter 메서드를 호출하여 롬복 커버리지 충족
    assertThat(chatMemberId.getRoomId()).isEqualTo(roomId);
    assertThat(chatMemberId.getUserId()).isEqualTo(userId);
  }

  @Test
  @DisplayName("EqualsAndHashCode 어노테이션 동작 검증 (동등성 비교)")
  void equalsAndHashCodeTest() {
    // Given
    UUID roomId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    // 필드 값이 완벽히 동일한 두 개의 다른 객체 생성
    ChatMemberId id1 = new ChatMemberId(roomId, userId);
    ChatMemberId id2 = new ChatMemberId(roomId, userId);

    // 필드 값이 다른 객체 생성
    ChatMemberId diffId = new ChatMemberId(roomId, UUID.randomUUID());

    // Then
    // 1. 값 객체 동등성(equals) 검증 (JaCoCo 분기 및 메서드 커버리지 획득)
    assertThat(id1).isEqualTo(id2); // 동등해야 함
    assertThat(id1).isNotEqualTo(diffId); // 달라야 함
    assertThat(id1).isNotEqualTo(null); // null과 비교 시 예외 없이 false여야 함

    // 2. 해시코드(hashCode) 일치 검증
    assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    assertThat(id1.hashCode()).isNotEqualTo(diffId.hashCode());
  }
}
