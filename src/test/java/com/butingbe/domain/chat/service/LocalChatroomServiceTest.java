package com.butingbe.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.butingbe.domain.auth.security.AuthenticatedUser;
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
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class LocalChatroomServiceTest {

  @Mock private LocalChatroomRepository localChatroomRepository;
  @Mock private ChatMemberRepository chatMemberRepository;
  @Mock private ChatMessageRepository chatMessageRepository;
  @Mock private UserRepository userRepository;
  @Mock private SimpMessagingTemplate messagingTemplate;

  @InjectMocks private LocalChatroomService localChatroomService;

  private UUID roomId;
  private UUID userId;
  private LocalChatroom mockChatroom;
  private User mockUser;

  @BeforeEach
  void setUp() {
    roomId = UUID.randomUUID();
    userId = UUID.randomUUID();

    mockChatroom = LocalChatroom.builder().chatZone(ChatZone.SUYEONG_NAMGU).maxMembers(30).build();
    setChatroomMembers(mockChatroom, 10); // 기본 인원 10명 세팅

    mockUser = User.builder().id(userId).nickname("수영구보안관").build();
  }

  private void setChatroomMembers(LocalChatroom chatroom, int current) {
    org.springframework.test.util.ReflectionTestUtils.setField(chatroom, "currentMembers", current);
  }

  // ==========================================
  // 📍 GET CHAT ROOM TESTS (채팅 내역 조회)
  // ==========================================

  @Test
  @DisplayName("처음 방 진입 시(lastMessageId가 null) 최신순 데이터를 오래된 순(역순)으로 정상 재정렬하여 반환한다")
  void getChatRoom_firstEnter_success() {
    // given
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(mockChatroom));

    ChatMessage msg1 = ChatMessage.builder().content("첫번째").userId(userId).build();
    ChatMessage msg2 = ChatMessage.builder().content("두번째").userId(UUID.randomUUID()).build();

    // DB에서 긁어온 최신순(Desc) 가짜 데이터 리스트
    List<ChatMessage> mockHistory = new ArrayList<>(List.of(msg2, msg1));
    when(chatMessageRepository.findTop100ByRoomIdOrderByCreatedAtDesc(roomId))
        .thenReturn(mockHistory);

    // when
    List<ChatMessageResponse> result = localChatroomService.getChatRoom(roomId, userId, null);

    // then
    assertThat(result).hasSize(2);
    assertThat(result.get(0).content()).isEqualTo("첫번째"); // 서비스 로직 내 Collections.reverse() 작동 검증
    assertThat(result.get(1).content()).isEqualTo("두번째");
    assertThat(result.get(0).isMine()).isTrue(); // 본인 작성 여부 플래그 체크
  }

  @Test
  @DisplayName("더보기 요청 시(lastMessageId 존재) 기준 메시지 이전의 대화 내역을 정상 조회하여 역순으로 반환한다")
  void getChatRoom_cursorPage_success() {
    // given
    UUID lastMessageId = UUID.randomUUID();
    OffsetDateTime lastMessageTime = OffsetDateTime.now();

    // 1. 프로덕션 빌더 그대로 생성 (이 시점에는 messageId가 null입니다)
    ChatMessage lastMessage =
        ChatMessage.builder().roomId(roomId).content("기준 메시지").userId(UUID.randomUUID()).build();

    // 2. 📌 Reflection을 이용해 private 필드인 messageId와 createdAt에 강제로 값 주입
    org.springframework.test.util.ReflectionTestUtils.setField(
        lastMessage, "messageId", lastMessageId);
    org.springframework.test.util.ReflectionTestUtils.setField(
        lastMessage, "createdAt", lastMessageTime);

    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(mockChatroom));
    when(chatMessageRepository.findById(lastMessageId)).thenReturn(Optional.of(lastMessage));

    ChatMessage pastMsg1 = ChatMessage.builder().content("더 옛날대화1").userId(userId).build();
    ChatMessage pastMsg2 =
        ChatMessage.builder().content("더 옛날대화2").userId(UUID.randomUUID()).build();
    List<ChatMessage> mockHistory = new ArrayList<>(List.of(pastMsg2, pastMsg1));

    // 3. 📌 시간 차이 미스매치를 방지하기 위해 Mockito 매처(any) 활용
    when(chatMessageRepository.findTop100ByRoomIdAndCursor(
            eq(roomId), any(OffsetDateTime.class), eq(lastMessageId)))
        .thenReturn(mockHistory);

    // when
    List<ChatMessageResponse> result =
        localChatroomService.getChatRoom(roomId, userId, lastMessageId);

    // then
    assertThat(result).hasSize(2);
    assertThat(result.get(0).content()).isEqualTo("더 옛날대화1");
  }

  @Test
  @DisplayName("존재하지 않는 방 ID로 오픈채팅 내역을 조회하면 예외를 던진다")
  void getChatRoom_fail_invalidRoom() {
    // given
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> localChatroomService.getChatRoom(roomId, userId, null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("error.chat.room.not_found");
  }

  @Test
  @DisplayName("더보기 요청 시 전달한 lastMessageId에 해당하는 메시지가 DB에 없으면 예외를 던진다")
  void getChatRoom_fail_invalidCursorMessage() {
    // given
    UUID invalidMessageId = UUID.randomUUID();
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(mockChatroom));
    when(chatMessageRepository.findById(invalidMessageId)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> localChatroomService.getChatRoom(roomId, userId, invalidMessageId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("error.chat.message.not_found");
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
    when(localChatroomRepository.increaseCurrentMembers(roomId)).thenReturn(1);

    // when
    localChatroomService.joinRoom(roomId, userId);

    // then
    verify(localChatroomRepository).increaseCurrentMembers(roomId);
    verify(chatMemberRepository).save(any(ChatMember.class));
  }

  @Test
  @DisplayName("가입하려는 오픈채팅방이 존재하지 않으면 예외를 던진다")
  void joinRoom_fail_invalidRoom() {
    // given
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> localChatroomService.joinRoom(roomId, userId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("error.chat.room.not_found");
  }

  @Test
  @DisplayName("가입하려는 사용자가 존재하지 않는 유저라면 예외를 던진다")
  void joinRoom_fail_invalidUser() {
    // given
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(mockChatroom));
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> localChatroomService.joinRoom(roomId, userId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("error.user.not_found");
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
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.chat.room.already_joined");
    verify(localChatroomRepository, never()).increaseCurrentMembers(any());
  }

  @Test
  @DisplayName("정원이 가득 차 갱신된 행이 없으면 가입을 거부하고 멤버를 저장하지 않는다")
  void joinRoom_fail_roomFull() {
    // given: 정원 확인은 update 의 where 절이 한다. 갱신 행 수 0이 곧 '자리 없음'이다.
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(mockChatroom));
    when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
    when(chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(false);
    when(localChatroomRepository.increaseCurrentMembers(roomId)).thenReturn(0);

    // when & then
    assertThatThrownBy(() -> localChatroomService.joinRoom(roomId, userId))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.chat.room.full");
    verify(chatMemberRepository, never()).save(any(ChatMember.class));
    verify(localChatroomRepository).increaseCurrentMembers(roomId);
  }

  // ==========================================
  // 📍 GET ROOMS BY ZONE TESTS
  // ==========================================

  @Test
  @DisplayName("권역 정보를 넘기면 해당 지역의 채팅방 리스트 DTO를 반환한다")
  void getRoomsByZone_success() {
    // given
    when(localChatroomRepository.findByChatZone(ChatZone.SUYEONG_NAMGU))
        .thenReturn(List.of(mockChatroom));

    // when
    List<ChatroomResponse> result = localChatroomService.getRoomsByZone(ChatZone.SUYEONG_NAMGU);

    // then
    assertThat(result).hasSize(1);
  }

  // ==========================================
  // 📍 SEND MESSAGE TESTS
  // ==========================================

  @Test
  @DisplayName("참여 중인 방에 보낸 메시지는 저장한 뒤 해당 방 구독자에게 발행한다")
  void sendMessage_success() {
    AuthenticatedUser sender =
        new AuthenticatedUser(userId, "user@example.com", "tester", List.of());
    when(chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(true);
    when(chatMessageRepository.save(any(ChatMessage.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    localChatroomService.sendMessage(roomId, sender, "안녕하세요");

    ArgumentCaptor<ChatMessage> savedCaptor = ArgumentCaptor.forClass(ChatMessage.class);
    verify(chatMessageRepository).save(savedCaptor.capture());
    assertThat(savedCaptor.getValue().getRoomId()).isEqualTo(roomId);
    assertThat(savedCaptor.getValue().getUserId()).isEqualTo(userId);
    assertThat(savedCaptor.getValue().getSenderNickname()).isEqualTo("tester");
    assertThat(savedCaptor.getValue().getContent()).isEqualTo("안녕하세요");

    ArgumentCaptor<ChatMessageResponse> publishedCaptor =
        ArgumentCaptor.forClass(ChatMessageResponse.class);
    verify(messagingTemplate)
        .convertAndSend(eq("/sub/chat/room/" + roomId), publishedCaptor.capture());
    assertThat(publishedCaptor.getValue().roomId()).isEqualTo(roomId);
    assertThat(publishedCaptor.getValue().senderNickname()).isEqualTo("tester");
    assertThat(publishedCaptor.getValue().content()).isEqualTo("안녕하세요");
    assertThat(publishedCaptor.getValue().isMine()).isNull();
  }

  @Test
  @DisplayName("참여하지 않은 방에 메시지를 보내면 ForbiddenException을 던진다")
  void sendMessage_rejectsNonParticipant() {
    AuthenticatedUser sender =
        new AuthenticatedUser(userId, "user@example.com", "tester", List.of());
    when(chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(false);

    assertThatThrownBy(() -> localChatroomService.sendMessage(roomId, sender, "안녕하세요"))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("error.chat.message.not_joined");

    verify(chatMessageRepository, never()).save(any(ChatMessage.class));
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
    verify(localChatroomRepository).decreaseCurrentMembers(roomId);
    verify(chatMemberRepository).deleteByIdRoomIdAndIdUserId(roomId, userId);
  }

  @Test
  @DisplayName("퇴장 시 존재하지 않는 채팅방 ID라면 예외를 던진다")
  void exitChatroom_fail_invalidRoom() {
    // given
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> localChatroomService.exitChatroom(roomId, userId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("error.chat.room.not_found");
  }

  @Test
  @DisplayName("퇴장 시 참여하고 있지 않은 채팅방이라면 예외를 던진다")
  void exitChatroom_fail_notParticipating() {
    // given
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(mockChatroom));
    when(chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(false);

    // when & then
    assertThatThrownBy(() -> localChatroomService.exitChatroom(roomId, userId))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("error.chat.room.not_joined");
  }

  // ==========================================
  // 📍 LIVE MEMBER COUNT TESTS (실시간 인원 수)
  // ==========================================

  @Test
  @DisplayName("실시간 입장 시 인원이 1 증가하고 상태 채널로 브로드캐스트한다")
  void enterLiveChatroom_incrementsAndBroadcasts() {
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(mockChatroom));
    when(localChatroomRepository.increaseCurrentMembers(roomId)).thenReturn(1);
    when(localChatroomRepository.findCurrentMembers(roomId)).thenReturn(Optional.of(11));

    localChatroomService.enterLiveChatroom(roomId);

    verify(messagingTemplate)
        .convertAndSend(
            eq("/sub/chat/room/" + roomId + "/status"),
            (Object) eq(Map.of("roomId", roomId, "currentMembers", 11)));
  }

  @Test
  @DisplayName("정원이 가득 찬 방에 실시간 입장하면 409로 거부한다")
  void enterLiveChatroom_rejectsFullRoom() {
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(mockChatroom));
    when(localChatroomRepository.increaseCurrentMembers(roomId)).thenReturn(0);

    assertThatThrownBy(() -> localChatroomService.enterLiveChatroom(roomId))
        .isInstanceOf(ConflictException.class)
        .hasMessage("error.chat.room.full");
    verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
  }

  @Test
  @DisplayName("존재하지 않는 방에 실시간 입장하면 404를 던진다")
  void enterLiveChatroom_rejectsUnknownRoom() {
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> localChatroomService.enterLiveChatroom(roomId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("error.chat.room.not_found");
  }

  @Test
  @DisplayName("실시간 퇴장 시 인원이 1 감소하고 상태 채널로 브로드캐스트한다")
  void exitLiveChatroom_decrementsAndBroadcasts() {
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(mockChatroom));
    when(localChatroomRepository.findCurrentMembers(roomId)).thenReturn(Optional.of(9));

    localChatroomService.exitLiveChatroom(roomId);

    verify(localChatroomRepository).decreaseCurrentMembers(roomId);
    verify(messagingTemplate)
        .convertAndSend(
            eq("/sub/chat/room/" + roomId + "/status"),
            (Object) eq(Map.of("roomId", roomId, "currentMembers", 9)));
  }

  @Test
  @DisplayName("인원이 0인 방에서 실시간 퇴장해도 음수로 내려가지 않는다")
  void exitLiveChatroom_doesNotGoBelowZero() {
    // 0 방어는 update 의 where currentMembers > 0 이 한다. 갱신 행이 없으니 값은 그대로 0이다.
    LocalChatroom emptyRoom =
        LocalChatroom.builder().chatZone(ChatZone.SUYEONG_NAMGU).maxMembers(30).build();
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.of(emptyRoom));
    when(localChatroomRepository.decreaseCurrentMembers(roomId)).thenReturn(0);
    when(localChatroomRepository.findCurrentMembers(roomId)).thenReturn(Optional.of(0));

    localChatroomService.exitLiveChatroom(roomId);

    verify(messagingTemplate)
        .convertAndSend(
            eq("/sub/chat/room/" + roomId + "/status"),
            (Object) eq(Map.of("roomId", roomId, "currentMembers", 0)));
  }

  @Test
  @DisplayName("존재하지 않는 방에서 실시간 퇴장하면 404를 던진다")
  void exitLiveChatroom_rejectsUnknownRoom() {
    when(localChatroomRepository.findById(roomId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> localChatroomService.exitLiveChatroom(roomId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("error.chat.room.not_found");
  }
}
