package com.butingbe.domain.travelteam.dto;


import com.butingbe.domain.travel.entity.Travel;
import lombok.Builder;

import java.util.UUID;


public record InviteVerificationResponse (

    UUID travelId,
    String travelName,
    Boolean valid

) {
    public static InviteVerificationResponse from(Travel travel, Boolean valid) {
        return new InviteVerificationResponse(
                travel.getId(),
                travel.getTitle(),
                valid
        );
    }
}