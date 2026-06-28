package com.butingbe.domain.chat.dto;

import com.butingbe.domain.chat.entity.LocalChatroom;

import java.math.BigDecimal;
import java.util.UUID;

public record ChatroomResponse(
        UUID roomId,
        String title,
        String landmarkName,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer currentMembers,
        Integer maxMembers
) {
    public static ChatroomResponse from(LocalChatroom chatroom) {
        return new ChatroomResponse(
                chatroom.getRoomId(),
                chatroom.getTitle(),
                chatroom.getLandmarkName(),
                chatroom.getLatitude(),
                chatroom.getLongitude(),
                chatroom.getCurrentMembers(),
                chatroom.getMaxMembers()
        );
    }
}
