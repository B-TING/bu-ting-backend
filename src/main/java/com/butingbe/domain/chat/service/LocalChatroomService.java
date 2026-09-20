package com.butingbe.domain.chat.service;

import com.butingbe.domain.chat.dto.ChatMessageResponse;
import com.butingbe.domain.chat.dto.ChatroomResponse;
import com.butingbe.domain.chat.entity.*;
import com.butingbe.domain.chat.repository.ChatMemberRepository;
import com.butingbe.domain.chat.repository.ChatMessageRepository;
import com.butingbe.domain.chat.repository.LocalChatroomRepository;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.ConflictException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocalChatroomService {

  private final LocalChatroomRepository localChatroomRepository;
  private final ChatMemberRepository chatMemberRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final UserRepository userRepository;
  private final SimpMessagingTemplate messagingTemplate;

  @Transactional(readOnly = true)
  public List<ChatMessageResponse> getChatRoom(UUID roomId, UUID userId, UUID lastMessageId) {

    localChatroomRepository
        .findById(roomId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 오픈채팅방입니다."));

    List<ChatMessage> chatHistory;

    if (lastMessageId == null) {
      // 1. 처음 방에 진입했을 때 (최신 100개)
      chatHistory = chatMessageRepository.findTop100ByRoomIdOrderByCreatedAtDesc(roomId);
    } else {
      // 2. 더보기 요청 시 기준 메시지 식별
      ChatMessage lastMessage =
          chatMessageRepository
              .findById(lastMessageId)
              .orElseThrow(() -> new IllegalArgumentException("기준이 되는 메시지가 존재하지 않습니다."));

      // 안전하게 시간과 ID를 추출하여 전달
      chatHistory =
          chatMessageRepository.findTop100ByRoomIdAndCursor(
              roomId, lastMessage.getCreatedAt(), lastMessage.getMessageId());
    }

    List<ChatMessageResponse> messageList =
        chatHistory.stream()
            .map(
                chatMessage ->
                    ChatMessageResponse.from(chatMessage, userId.equals(chatMessage.getUserId())))
            .collect(Collectors.toList());

    Collections.reverse(messageList);

    return messageList;
  }

  public List<ChatroomResponse> getRoomsByZone(ChatZone zone) {
    return localChatroomRepository.findByChatZone(zone).stream()
        .map(ChatroomResponse::from)
        .toList();
  }

  @Transactional
  public void exitChatroom(UUID roomId, UUID userId) {
    requireRoom(roomId);

    if (!chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)) {
      throw new IllegalArgumentException("참여하고 있지 않은 채팅방입니다.");
    }

    chatMemberRepository.deleteByIdRoomIdAndIdUserId(roomId, userId);
    localChatroomRepository.decreaseCurrentMembers(roomId);
  }

  @Transactional
  public void joinRoom(UUID roomId, UUID userId) {
    LocalChatroom chatroom =
        localChatroomRepository
            .findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 오픈채팅방입니다."));

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    if (chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)) {
      throw new ConflictException("error.chat.room.already_joined");
    }

    // 정원 확인과 인원 증가를 한 문장으로 한다. 읽고 비교한 뒤 증가시키면 동시 입장에서 정원을 넘긴다.
    // 갱신된 행이 없으면 그 사이 다른 사람이 마지막 자리를 채운 것이다.
    if (localChatroomRepository.increaseCurrentMembers(roomId) == 0) {
      throw new ConflictException("error.chat.room.full");
    }

    chatMemberRepository.save(ChatMember.builder().chatroom(chatroom).user(user).build());
  }

  @Transactional
  public void enterLiveChatroom(UUID roomId) {
    requireRoom(roomId);

    if (localChatroomRepository.increaseCurrentMembers(roomId) == 0) {
      throw new ConflictException("error.chat.room.full");
    }

    broadcastRoomStatus(roomId, currentMembers(roomId));
  }

  @Transactional
  public void exitLiveChatroom(UUID roomId) {
    requireRoom(roomId);
    localChatroomRepository.decreaseCurrentMembers(roomId);

    broadcastRoomStatus(roomId, currentMembers(roomId));
  }

  // 💡 실시간 브로드캐스팅 공통 메서드
  private void broadcastRoomStatus(UUID roomId, int currentMembers) {
    Object statusPayload =
        Map.of(
            "roomId", roomId,
            "currentMembers", currentMembers);

    messagingTemplate.convertAndSend("/sub/chat/room/" + roomId + "/status", statusPayload);
  }

  private LocalChatroom requireRoom(UUID roomId) {
    return localChatroomRepository
        .findById(roomId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채팅방입니다."));
  }

  /** 벌크 갱신 직후의 인원수. 영속성 컨텍스트에 남은 엔티티는 갱신 전 값을 들고 있다. */
  private int currentMembers(UUID roomId) {
    return localChatroomRepository.findCurrentMembers(roomId).orElse(0);
  }
}
