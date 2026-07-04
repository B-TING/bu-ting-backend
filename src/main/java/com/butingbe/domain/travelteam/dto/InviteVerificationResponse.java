package com.butingbe.domain.travelteam.dto;

import com.butingbe.domain.chat.dto.ChatMessageResponse;
import com.butingbe.domain.chat.entity.ChatMessage;
import com.butingbe.domain.temp.entity.TravelTemp;
import lombok.Builder;

import java.util.UUID;


public record InviteVerificationResponse (

    UUID travelId,
    String travelName,
    Boolean valid

) {
    public static InviteVerificationResponse from(TravelTemp travel, Boolean valid) {
        return new InviteVerificationResponse(
                travel.getId(),
                travel.getTitle(),
                valid
        );
    }
}