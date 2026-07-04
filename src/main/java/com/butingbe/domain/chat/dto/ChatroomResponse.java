package com.butingbe.domain.chat.dto;

import com.butingbe.domain.chat.entity.LocalChatroom;
import java.util.UUID;

public record ChatroomResponse(
    UUID roomId,
    String title,
    String chatZone,
    String description,
    Integer currentMembers,
    Integer maxMembers) {
  public static ChatroomResponse from(LocalChatroom chatroom) {
    return new ChatroomResponse(
        chatroom.getRoomId(),
        chatroom.getTitle(),
        chatroom.getChatZone().getZoneName(),
        chatroom.getDescription(),
        chatroom.getCurrentMembers(),
        chatroom.getMaxMembers());
  }
}
