package com.butingbe.domain.chat.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class LocalChatroomTest {

    @Test
    @DisplayName("LocalChatroom 빌더 생성자 및 초기화 로직 검증")
    void localChatroomBuilderTest() {
        // When
        LocalChatroom chatroom = LocalChatroom.builder()
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

    @Test
    @DisplayName("유저 입장 시 현재 인원수가 1명 증가한다 (정상 케이스)")
    void incrementCurrentMembers_success() {
        // Given
        LocalChatroom chatroom = LocalChatroom.builder()
                .maxMembers(2)
                .build();

        // When
        chatroom.incrementCurrentMembers();

        // Then
        assertThat(chatroom.getCurrentMembers()).isEqualTo(1);
    }

    @Test
    @DisplayName("정원이 가득 찬 상태에서 유저가 입장하면 IllegalStateException 예외가 발생한다 (예외 분기 커버리지)")
    void incrementCurrentMembers_throwsException_whenRoomIsFull() {
        // Given
        LocalChatroom chatroom = LocalChatroom.builder()
                .maxMembers(1)
                .build();
        chatroom.incrementCurrentMembers(); // 인원을 1명으로 만들어 정원을 채움

        // When & Then
        // 💡 이 예외가 터지는 흐름을 타야 if (this.currentMembers >= this.maxMembers) 분기문이 완벽하게 커버됩니다!
        assertThatThrownBy(chatroom::incrementCurrentMembers)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("채팅방 정원이 가득 찼습니다.");
    }

    @Test
    @DisplayName("유저 퇴장 시 현재 인원수가 1명 차감되며, 0명 미만으로는 내려가지 않는다")
    void decrementCurrentMembers_successAndBoundaryCheck() {
        // Given
        LocalChatroom chatroom = LocalChatroom.builder()
                .maxMembers(5)
                .build();

        chatroom.incrementCurrentMembers(); // 0 -> 1명
        chatroom.incrementCurrentMembers(); // 1 -> 2명

        // When (정상 차감 분기)
        chatroom.decrementCurrentMembers(); // 2 -> 1명

        // Then
        assertThat(chatroom.getCurrentMembers()).isEqualTo(1);

        // When (0명일 때 차감하더라도 0명 유지하는 if 분기 검증)
        chatroom.decrementCurrentMembers(); // 1 -> 0명
        chatroom.decrementCurrentMembers(); // 0명 상태에서 한 번 더 차감 시 시도

        // Then
        // 💡 0명 밑으로 가지 않고 0으로 방어되는 if (this.currentMembers > 0) 분기 완전 커버
        assertThat(chatroom.getCurrentMembers()).isEqualTo(0);
    }
}