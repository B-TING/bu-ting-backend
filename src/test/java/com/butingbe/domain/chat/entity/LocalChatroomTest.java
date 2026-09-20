package com.butingbe.domain.chat.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocalChatroomTest {

  @Test
  @DisplayName("LocalChatroom 빌더 생성자 및 초기화 로직 검증")
  void localChatroomBuilderTest() {
    // When
    LocalChatroom chatroom =
        LocalChatroom.builder()
            .title("수영구 맛집 탐방방")
            .description("맛있는 거 같이 먹어요")
            .chatZone(ChatZone.SUYEONG_NAMGU)
            .maxMembers(30)
            .build();

    // Then
    assertThat(chatroom.getTitle()).isEqualTo("수영구 맛집 탐방방");
    assertThat(chatroom.getDescription()).isEqualTo("맛있는 거 같이 먹어요");
    assertThat(chatroom.getChatZone()).isEqualTo(ChatZone.SUYEONG_NAMGU);
    assertThat(chatroom.getMaxMembers()).isEqualTo(30);

    // 💡 중요: 생성자 내부에서 강제 초기화한 값 검증 (JaCoCo 커버리지 충족)
    assertThat(chatroom.getCurrentMembers()).isEqualTo(0);
    assertThat(chatroom.getRoomId()).isNull(); // DB 저장 전이므로 null
  }
}
