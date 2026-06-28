package com.butingbe.domain.chat.repository;

import com.butingbe.domain.chat.entity.ChatMember;
import com.butingbe.domain.chat.entity.ChatMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMemberRepository extends JpaRepository<ChatMember, ChatMemberId> {

    void deleteByIdRoomIdAndIdUserId(UUID roomId, UUID userId);

    Optional<ChatMember> findByIdRoomIdAndIdUserId(UUID roomId, UUID userId);

    @Query("SELECT cm FROM ChatMember cm JOIN FETCH cm.chatroom WHERE cm.id.userId = :userId ORDER BY cm.joinedAt DESC")
    List<ChatMember> findAllByUserIdWithChatRooms(@Param("userId") UUID userId);

    boolean existsByIdRoomIdAndIdUserId(UUID roomId, UUID userId);
}
