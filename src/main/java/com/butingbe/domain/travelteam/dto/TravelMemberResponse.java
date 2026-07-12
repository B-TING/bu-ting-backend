package com.butingbe.domain.travelteam.dto;

import com.butingbe.domain.travelteam.entity.TravelMember;
import com.butingbe.domain.travelteam.entity.TravelTeamRole;
import java.util.UUID;

public record TravelMemberResponse(
    UUID memberId,
    UUID userId,
    String email,
    String nickname,
    String profileImageUrl,
    TravelTeamRole role) {

  public static TravelMemberResponse from(TravelMember member) {
    return new TravelMemberResponse(
        member.getId(),
        member.getUser().getId(),
        member.getUser().getEmail(),
        member.getUser().getNickname(),
        member.getUser().getProfileImageUrl(),
        member.getRole());
  }
}
