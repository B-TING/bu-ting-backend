package com.butingbe.domain.chat.entity;

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

  private UUID roomId;
  private UUID userId;
  private String senderNickname;
  private String content;
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
