package com.butingbe.domain.chat.service;

import com.butingbe.domain.chat.dto.ChatroomCreateRequest;
import com.butingbe.domain.chat.dto.ChatroomResponse;
import com.butingbe.domain.chat.entity.ChatMember;
import com.butingbe.domain.chat.entity.ChatMemberId;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.chat.entity.LocalChatroom;
import com.butingbe.domain.chat.repository.ChatMemberRepository;
import com.butingbe.domain.chat.repository.LocalChatroomRepository;
import com.butingbe.global.error.exception.DuplicateResourceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocalChatroomService {

    private final LocalChatroomRepository localChatroomRepository;
    private final ChatMemberRepository chatMemberRepository;

    /**
     * 1. 범위 기반 오픈채팅방 리스트 조회
     */
    public List<ChatroomResponse> getRoomsWithinBounds(BigDecimal swLat, BigDecimal swLng, BigDecimal neLat, BigDecimal neLng) {
        return localChatroomRepository.findChatroomsWithinBounds(swLat, swLng, neLat, neLng)
                .stream()
                .map(ChatroomResponse::from)
                .toList();
    }


    public List<ChatroomResponse> getRoomsByZone(ChatZone zone) {
        List<String> prefixes = zone.getCityCodes();
        return localChatroomRepository.findByLocalCodePrefixIn(prefixes)
                .stream()
                .map(ChatroomResponse::from)
                .toList();
    }

    @Transactional
    public void joinChatroom(UUID roomId, UUID userId) {
        // 해당 채팅방이 존재하는지 조회
        LocalChatroom chatroom = localChatroomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채팅방입니다."));

        // 중복 가입 검증: 이미 해당 방의 멤버라면 추가 로직 없이 무시하거나 예외 투척
        if (chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)) {
            throw new DuplicateResourceException("error.chatroom.already_joined"); // 기존 핸들러에 맞춤
        }

        // 정원 초과 검증: 현재 인원이 최대 인원과 같거나 크다면 입장 차단
        if (chatroom.getCurrentMembers() >= chatroom.getMaxMembers()) {
            throw new IllegalStateException("채팅방 정원이 가득 찼습니다."); // GlobalHandler가 409(CONFLICT)로 처리
        }

        ChatMember newMember = ChatMember.builder()
                .chatroom(chatroom)
                .userId(userId)
                .build();
        chatMemberRepository.save(newMember);

        // 채팅방 인원수 증가
        chatroom.incrementCurrentMembers();

    }


    @Transactional
    public void exitChatroom(UUID roomId, UUID userId) {
        LocalChatroom chatroom = localChatroomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채팅방입니다."));

        if (!chatMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)) {
            throw new IllegalArgumentException("참여하고 있지 않은 채팅방입니다.");
        }

        chatMemberRepository.deleteByIdRoomIdAndIdUserId(roomId, userId);
        chatroom.decrementCurrentMembers();

    }
}
