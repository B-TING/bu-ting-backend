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
import java.util.stream.Collectors;
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

  public List<ChatMessageResponse> getChatRoom(UUID roomId, UUID userId) {
    LocalChatroom chatroom =
        localChatroomRepository
            .findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 오픈채팅방입니다."));

    List<ChatMessage> chatHistory =
        chatMessageRepository.findTop100ByRoomIdOrderByCreatedAtDesc(roomId);

    List<ChatMessageResponse> messageList =
        chatHistory.stream()
            .map(
                chatMessage ->
                    ChatMessageResponse.from(chatMessage, userId.equals(chatMessage.getUserId())))
            .collect(
                Collectors.toList()); // 💡 .toList() 대신 이걸 써야 Collections.reverse 시 500 에러가 안 납니다!

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
    if (!chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)) {
      if (chatroom.getCurrentMembers() >= chatroom.getMaxMembers()) {
        throw new IllegalStateException("채팅방 정원이 가득 찼습니다."); // GlobalHandler가 409(CONFLICT)로 처리
      }

      ChatMember newMember = ChatMember.builder().chatroom(chatroom).user(user).build();
      chatMemberRepository.save(newMember);

      // 채팅방 인원수 증가
      chatroom.incrementCurrentMembers();
    } else {
      throw new IllegalStateException("이미 가입한 사용자입니다.");
    }
  }
}
