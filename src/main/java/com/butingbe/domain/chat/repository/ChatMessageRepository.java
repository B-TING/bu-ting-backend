package com.butingbe.domain.chat.repository;

import com.butingbe.domain.chat.entity.ChatMessage;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

  List<ChatMessage> findTop100ByRoomIdOrderByCreatedAtDesc(UUID roomId);

  @Query(
      "SELECT cm FROM ChatMessage cm "
          + "WHERE cm.roomId = :roomId "
          + "AND ("
          + "  cm.createdAt < :lastCreatedAt "
          + "  OR "
          + "  (cm.createdAt = :lastCreatedAt AND cm.messageId < :lastMessageId)"
          + ") "
          + "ORDER BY cm.createdAt DESC, cm.messageId DESC")
  List<ChatMessage> findTop100ByRoomIdAndCursor(
      @Param("roomId") UUID roomId,
      @Param("lastCreatedAt") OffsetDateTime lastCreatedAt,
      @Param("lastMessageId") UUID lastMessageId);
}
