package com.butingbe.domain.chat.repository;

import com.butingbe.domain.chat.entity.ChatMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

  List<ChatMessage> findTop100ByRoomIdOrderByCreatedAtDesc(UUID roomId);
}
