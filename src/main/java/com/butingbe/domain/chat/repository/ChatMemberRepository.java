package com.butingbe.domain.chat.repository;

import com.butingbe.domain.chat.entity.ChatMember;
import com.butingbe.domain.chat.entity.ChatMemberId;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMemberRepository extends JpaRepository<ChatMember, ChatMemberId> {

  void deleteByIdRoomIdAndIdUserId(UUID roomId, UUID userId);

  boolean existsByIdRoomIdAndIdUserId(UUID roomId, UUID userId);
}
