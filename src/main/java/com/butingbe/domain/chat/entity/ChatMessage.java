package com.butingbe.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {
  @Id
  @GeneratedValue(generator = "UUID")
  private UUID messageId;

  @Column(name = "room_id", nullable = false)
  private UUID roomId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "sender_nickname", nullable = false, length = 100)
  private String senderNickname;

  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @Builder
  public ChatMessage(UUID roomId, UUID userId, String senderNickname, String content) {
    this.roomId = roomId;
    this.userId = userId;
    this.senderNickname = senderNickname;
    this.content = content;
    this.createdAt = OffsetDateTime.now();
  }
}
