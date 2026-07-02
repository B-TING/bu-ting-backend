package com.butingbe.domain.chat.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import java.time.OffsetDateTime;
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

        // 도메인 엔티티 모킹용 기본 셋업 (기본 정원 30명, 현재 10명 설정)
        mockChatroom = LocalChatroom.builder()
                .chatZone(ChatZone.SUYEONG_NAMGU)
                .maxMembers(30)
                .build();
        // Reflection 또는 인원 세팅용 로직 대행 (엔티티 내부에 세터가 없다면 테스트용 필드 사용)
        setChatroomMembers(mockChatroom, 10);

        mockUser = User.builder().id(userId).nickname("수영구보안관").build();
    }

    private void setChatroomMembers(LocalChatroom chatroom, int current) {
        // 엔티티 구조에 맞게 인원수 조절 로직 대행 (필요시 반영)
        while (chatroom.getCurrentMembers() < current) {
            chatroom.incrementCurrentMembers();
        }
    }

    // ==========================================
    // 📍 ENTER CHAT ROOM TESTS
    // ==========================================

    @Test
    @DisplayName("이미 참여 중인 사용자가 입장하면 인원수 변화 없이 기존 메시지 내역을 오래된 순(정렬 반전)으로 반환한다")
    void enterChatRoom_success_existingMember() {
        // given
        when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(mockChatroom));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(true);

        ChatMessage msg1 = ChatMessage.builder().content("첫번째").userId(userId).build();
        ChatMessage msg2 = ChatMessage.builder().content("두번째").userId(UUID.randomUUID()).build();
        // Descending(최신순) 정렬 상태의 가짜 DB 응답
        when(chatMessageRepository.findTop100ByRoomIdOrderByCreatedAtDesc(roomId)).thenReturn(List.of(msg2, msg1));

        // when
        List<ChatMessageResponse> result = localChatroomService.enterChatRoom(roomId, userId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).content()).isEqualTo("첫번째"); // 💡 내역이 역순으로 재정렬되었는지 확인
        assertThat(result.get(1).content()).isEqualTo("두번째");
        assertThat(result.get(0).isMine()).isTrue(); // 본인 메시지 플래그 매핑 검증
        assertThat(mockChatroom.getCurrentMembers()).isEqualTo(10); // 인원수 그대로 유지
        verify(chatMemberRepository, never()).save(any(ChatMember.class));
    }

    @Test
    @DisplayName("신규 사용자가 입장하면 방 정원을 체크한 후 신규 멤버로 등록하고 방 인원수를 1 증가시킨다")
    void enterChatRoom_success_newMember() {
        // given
        when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(mockChatroom));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(false);
        when(chatMessageRepository.findTop100ByRoomIdOrderByCreatedAtDesc(roomId)).thenReturn(new ArrayList<>());

        // when
        localChatroomService.enterChatRoom(roomId, userId);

        // then
        assertThat(mockChatroom.getCurrentMembers()).isEqualTo(11); // 💡 인원수 10 -> 11 증가 검증
        verify(chatMemberRepository).save(any(ChatMember.class)); // 멤버 세이브 호출 검증
    }

    @Test
    @DisplayName("존재하지 않는 방 ID로 입장 시 예외를 던진다")
    void enterChatRoom_fail_invalidRoom() {
        // given
        when(localChatroomRepository.findById(roomId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> localChatroomService.enterChatRoom(roomId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 오픈채팅방입니다.");
    }

    @Test
    @DisplayName("존재하지 않는 사용자 ID로 입장 시 예외를 던진다")
    void enterChatRoom_fail_invalidUser() {
        // given
        when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(mockChatroom));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> localChatroomService.enterChatRoom(roomId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 사용자입니다.");
    }

    @Test
    @DisplayName("신규 입장 시 채팅방 정원이 이미 가득 차 있다면 예외를 던진다")
    void enterChatRoom_fail_roomFull() {
        // given
        LocalChatroom fullChatroom = LocalChatroom.builder().chatZone(ChatZone.SUYEONG_NAMGU).maxMembers(10).build();
        setChatroomMembers(fullChatroom, 10); // 10명 정원에 10명 가득 참

        when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(fullChatroom));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> localChatroomService.enterChatRoom(roomId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("채팅방 정원이 가득 찼습니다.");
    }

    // ==========================================
    // 📍 GET ROOMS BY ZONE TESTS
    // ==========================================

    @Test
    @DisplayName("권역 정보를 넘기면 해당 지역에 매핑된 채팅방 리스트 DTO를 성공적으로 조회한다")
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
    @DisplayName("정상적으로 채팅방 나가기 요청을 처리하면 멤버 목록에서 삭제하고 인원수를 1 감소시킨다")
    void exitChatroom_success() {
        // given
        when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(mockChatroom));
        when(chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(true);

        // when
        localChatroomService.exitChatroom(roomId, userId);

        // then
        assertThat(mockChatroom.getCurrentMembers()).isEqualTo(9); // 💡 인원수 10 -> 9 감소 검증
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