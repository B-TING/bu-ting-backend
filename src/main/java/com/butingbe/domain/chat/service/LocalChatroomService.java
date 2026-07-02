package com.butingbe.domain.chat.service;

import com.butingbe.domain.chat.dto.ChatMessageResponse;
import com.butingbe.domain.chat.dto.ChatroomResponse;
import com.butingbe.domain.chat.entity.*;
import com.butingbe.domain.chat.repository.ChatMemberRepository;
import com.butingbe.domain.chat.repository.ChatMessageRepository;
import com.butingbe.domain.chat.repository.LocalChatroomRepository;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

  @Transactional(readOnly = true)
  public List<ChatMessageResponse> enterChatRoom(UUID roomId, UUID userId) {
    // 1. 존재하는 방인지 먼저 검증 (방이 없으면 예외 던지기)
    LocalChatroom chatroom =
        localChatroomRepository
            .findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 오픈채팅방입니다."));

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    if (!chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)) {
      if (chatroom.getCurrentMembers() >= chatroom.getMaxMembers()) {
        throw new IllegalStateException("채팅방 정원이 가득 찼습니다."); // GlobalHandler가 409(CONFLICT)로 처리
      }

      ChatMember newMember = ChatMember.builder().chatroom(chatroom).user(user).build();
      chatMemberRepository.save(newMember);

      // 채팅방 인원수 증가
      chatroom.incrementCurrentMembers();
    }

    // 2. 해당 방의 과거 메시지 내역 최신순 100개 조회
    List<ChatMessage> chatHistory =
        chatMessageRepository.findTop100ByRoomIdOrderByCreatedAtDesc(roomId);

    // 3. 엔티티를 DTO 리스트로 변환해서 반환
    List<ChatMessageResponse> messageList =
        chatHistory.stream()
            .map(
                chatMessage ->
                    ChatMessageResponse.from(chatMessage, userId.equals(chatMessage.getUserId())))
            .toList();

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
    LocalChatroom chatroom =
        localChatroomRepository
            .findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채팅방입니다."));

    if (!chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)) {
      throw new IllegalArgumentException("참여하고 있지 않은 채팅방입니다.");
    }

    chatMemberRepository.deleteByIdRoomIdAndIdUserId(roomId, userId);
    chatroom.decrementCurrentMembers();
  }
}
