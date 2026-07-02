package com.butingbe.domain.chat.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.butingbe.domain.user.entity.User;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatMemberTest {

  @Test
  @DisplayName("ChatMember 빌더 생성자 및 초기화 로직 커버리지 검증")
  void chatMemberBuilderTest() {
    // Given
    // 가짜(Mock) 객체를 사용해 복잡한 DB 제약조건 우회 및 순수 객체 생성
    LocalChatroom mockRoom = mock(LocalChatroom.class);
    User mockUser = mock(User.class);

    UUID roomId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    when(mockRoom.getRoomId()).thenReturn(roomId);
    when(mockUser.getId()).thenReturn(userId);

    // When
    // 💡 엔티티 내부의 @Builder 생성자 라인을 직접 실행 (JaCoCo 커버리지 충족)
    ChatMember chatMember = ChatMember.builder().chatroom(mockRoom).user(mockUser).build();

    // Then
    // 각 필드의 Getter와 생성 시점 초기화 로직 검증 (JaCoCo 라인 커버리지 충족)
    assertThat(chatMember.getId()).isNotNull();
    assertThat(chatMember.getId().getRoomId()).isEqualTo(roomId);
    assertThat(chatMember.getId().getUserId()).isEqualTo(userId);
    assertThat(chatMember.getChatroom()).isEqualTo(mockRoom);
    assertThat(chatMember.getUser()).isEqualTo(mockUser);
    assertThat(chatMember.getJoinedAt()).isNotNull();
    assertThat(chatMember.getLastReadAt()).isNotNull();
  }

  @Test
  @DisplayName("updateLastReadAt 메서드 호출 시 lastReadAt 시간이 갱신된다")
  void updateLastReadAt_success() {
    // Given
    LocalChatroom mockRoom = mock(LocalChatroom.class);
    User mockUser = mock(User.class);

    ChatMember chatMember = ChatMember.builder().chatroom(mockRoom).user(mockUser).build();

    OffsetDateTime originalReadAt = chatMember.getLastReadAt();

    // 테스트 도중 미세한 시간 차이를 만들기 위한 짧은 대기
    try {
      Thread.sleep(1);
    } catch (InterruptedException e) {
      Thread.ofVirtual().start(() -> {});
    }

    // When
    // 💡 엔티티 내부의 비즈니스 메서드 라인 실행 (JaCoCo 커버리지 충족)
    chatMember.updateLastReadAt();

    // Then
    assertThat(chatMember.getLastReadAt()).isAfter(originalReadAt);
  }
}
