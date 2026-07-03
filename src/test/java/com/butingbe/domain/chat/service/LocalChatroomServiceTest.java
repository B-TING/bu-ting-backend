package com.butingbe.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.chat.dto.ChatMessageResponse;
import com.butingbe.domain.chat.dto.ChatroomResponse;
import com.butingbe.domain.chat.entity.ChatMember;
import com.butingbe.domain.chat.entity.ChatMessage;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.chat.entity.LocalChatroom;
import com.butingbe.domain.chat.repository.ChatMemberRepository;
import com.butingbe.domain.chat.repository.ChatMessageRepository;
import com.butingbe.domain.chat.repository.LocalChatroomRepository;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalChatroomServiceTest {

  @Mock private LocalChatroomRepository localChatroomRepository;
  @Mock private ChatMemberRepository chatMemberRepository;
  @Mock private ChatMessageRepository chatMessageRepository;
  @Mock private UserRepository userRepository;

  @InjectMocks
  private LocalChatroomService localChatroomService;

  private UUID roomId;
  private UUID userId;
  private LocalChatroom mockChatroom;
  private User mockUser;

  @BeforeEach
  void setUp() {
    roomId = UUID.randomUUID();
    userId = UUID.randomUUID();

    mockChatroom = LocalChatroom.builder()
            .chatZone(ChatZone.SUYEONG_NAMGU)
            .maxMembers(30)
            .build();
    setChatroomMembers(mockChatroom, 10); // 기본 인원 10명 세팅

    mockUser = User.builder().id(userId).nickname("수영구보안관").build();
  }

  private void setChatroomMembers(LocalChatroom chatroom, int current) {
    while (chatroom.getCurrentMembers() < current) {
      chatroom.incrementCurrentMembers();
    }
  }

  // ==========================================
  // 📍 GET CHAT ROOM TESTS (채팅 내역 조회)
  // ==========================================

  @Test
  @DisplayName("존재하는 방의 대화 내역 조회 시 최신순 데이터를 오래된 순(역순)으로 정상 재정렬하여 반환한다")
  void getChatRoom_success() {
    // given
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(mockChatroom));

    ChatMessage msg1 = ChatMessage.builder().content("첫번째").userId(userId).build();
    ChatMessage msg2 = ChatMessage.builder().content("두번째").userId(UUID.randomUUID()).build();

    // 💡 DB에서 긁어온 최신순(Desc) 가짜 데이터 리스트
    List<ChatMessage> mockHistory = new ArrayList<>(List.of(msg2, msg1));
    when(chatMessageRepository.findTop100ByRoomIdOrderByCreatedAtDesc(roomId)).thenReturn(mockHistory);

    // when
    List<ChatMessageResponse> result = localChatroomService.getChatRoom(roomId, userId);

    // then
    assertThat(result).hasSize(2);
    assertThat(result.get(0).content()).isEqualTo("첫번째"); // 서비스 로직 내 Collections.reverse() 작동 검증
    assertThat(result.get(1).content()).isEqualTo("두번째");
    assertThat(result.get(0).isMine()).isTrue(); // 본인 작성 여부 플래그 체크
  }

  @Test
  @DisplayName("존재하지 않는 방 ID로 채팅 내역을 조회하면 예외를 던진다")
  void getChatRoom_fail_invalidRoom() {
    // given
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> localChatroomService.getChatRoom(roomId, userId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("존재하지 않는 오픈채팅방입니다.");
  }

  // ==========================================
  // 📍 JOIN ROOM TESTS (채팅방 가입)
  // ==========================================

  @Test
  @DisplayName("신규 사용자가 정원에 여유가 있는 방에 가입 신청을 하면 가입에 성공하고 인원수가 1 증가한다")
  void joinRoom_success() {
    // given
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(mockChatroom));
    when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
    when(chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(false);

    // when
    localChatroomService.joinRoom(roomId, userId);

    // then
    assertThat(mockChatroom.getCurrentMembers()).isEqualTo(11); // 10 -> 11명 증가 검증
    verify(chatMemberRepository).save(any(ChatMember.class));
  }

  @Test
  @DisplayName("가입하려는 오픈채팅방이 존재하지 않으면 예외를 던진다")
  void joinRoom_fail_invalidRoom() {
    // given
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> localChatroomService.joinRoom(roomId, userId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("존재하지 않는 오픈채팅방입니다.");
  }

  @Test
  @DisplayName("가입하려는 사용자가 존재하지 않는 유저라면 예외를 던진다")
  void joinRoom_fail_invalidUser() {
    // given
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(mockChatroom));
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> localChatroomService.joinRoom(roomId, userId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("존재하지 않는 사용자입니다.");
  }

  @Test
  @DisplayName("이미 가입한 사용자가 다시 가입 신청을 하면 예외를 던진다")
  void joinRoom_fail_alreadyJoined() {
    // given
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(mockChatroom));
    when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
    when(chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(true);

    // when & then
    assertThatThrownBy(() -> localChatroomService.joinRoom(roomId, userId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("이미 가입한 사용자입니다.");
  }

  @Test
  @DisplayName("신규 가입이지만 방 정원이 가득 찬 상태라면 예외를 던진다")
  void joinRoom_fail_roomFull() {
    // given
    LocalChatroom fullChatroom = LocalChatroom.builder().chatZone(ChatZone.SUYEONG_NAMGU).maxMembers(10).build();
    setChatroomMembers(fullChatroom, 10); // 10명 정원에 10명 가득 찬 상태로 모킹

    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(fullChatroom));
    when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
    when(chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(false);

    // when & then
    assertThatThrownBy(() -> localChatroomService.joinRoom(roomId, userId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("채팅방 정원이 가득 찼습니다.");
  }

  // ==========================================
  // 📍 GET ROOMS BY ZONE TESTS
  // ==========================================

  @Test
  @DisplayName("권역 정보를 넘기면 해당 지역의 채팅방 리스트 DTO를 반환한다")
  void getRoomsByZone_success() {
    // given
    when(localChatroomRepository.findByChatZone(ChatZone.SUYEONG_NAMGU)).thenReturn(List.of(mockChatroom));

    // when
    List<ChatroomResponse> result = localChatroomService.getRoomsByZone(ChatZone.SUYEONG_NAMGU);

    // then
    assertThat(result).hasSize(1);
  }

  // ==========================================
  // 📍 EXIT CHAT ROOM TESTS
  // ==========================================

  @Test
  @DisplayName("참여 중인 방에서 나가기 요청을 하면 멤버 목록에서 삭제하고 인원수를 1 감소시킨다")
  void exitChatroom_success() {
    // given
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(mockChatroom));
    when(chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(true);

    // when
    localChatroomService.exitChatroom(roomId, userId);

    // then
    assertThat(mockChatroom.getCurrentMembers()).isEqualTo(9); // 10 -> 9명 감소 검증
    verify(chatMemberRepository).deleteByIdRoomIdAndIdUserId(roomId, userId);
  }

  @Test
  @DisplayName("퇴장 시 존재하지 않는 채팅방 ID라면 예외를 던진다")
  void exitChatroom_fail_invalidRoom() {
    // given
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> localChatroomService.exitChatroom(roomId, userId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("존재하지 않는 채팅방입니다.");
  }

  @Test
  @DisplayName("퇴장 시 참여하고 있지 않은 채팅방이라면 예외를 던진다")
  void exitChatroom_fail_notParticipating() {
    // given
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(mockChatroom));
    when(chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(false);

    // when & then
    assertThatThrownBy(() -> localChatroomService.exitChatroom(roomId, userId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("참여하고 있지 않은 채팅방입니다.");
  }
}
