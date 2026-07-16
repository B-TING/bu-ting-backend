package com.butingbe.domain.chat.dto;

import com.butingbe.domain.chat.entity.ChatMessage;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatMessageResponse(
    UUID messageId,
    UUID roomId,
    UUID userId,
    String senderNickname,
    String content,
    OffsetDateTime createdAt,
    Boolean isMine) {
  public static ChatMessageResponse from(ChatMessage entity, Boolean isMine) {
    return new ChatMessageResponse(
        entity.getMessageId(),
        entity.getRoomId(),
        entity.getUserId(),
        entity.getSenderNickname(),
        entity.getContent(),
        entity.getCreatedAt(),
        isMine);
  }

  public UUID id() {
    return messageId;
  }
}
