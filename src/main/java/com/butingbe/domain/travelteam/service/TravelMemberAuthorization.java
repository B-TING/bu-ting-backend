package com.butingbe.domain.travelteam.service;

import com.butingbe.domain.travelteam.entity.TravelMember;
import com.butingbe.domain.travelteam.entity.TravelTeamRole;
import com.butingbe.domain.travelteam.repository.TravelMemberRepository;
import com.butingbe.global.error.exception.ForbiddenException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TravelMemberAuthorization {

  private static final String NOT_MEMBER_MESSAGE = "User is not a travel member.";

  private final TravelMemberRepository travelMemberRepository;

  public void validateMember(UUID travelId, UUID userId) {
    requireMember(travelId, userId);
  }

  public TravelMember requireMember(UUID travelId, UUID userId) {
    return travelMemberRepository
        .findByTravel_IdAndUser_Id(travelId, userId)
        .orElseThrow(() -> new ForbiddenException(NOT_MEMBER_MESSAGE));
  }

  public TravelMember requireLeader(UUID travelId, UUID userId, String message) {
    TravelMember member = requireMember(travelId, userId);
    if (member.getRole() != TravelTeamRole.LEADER) {
      throw new ForbiddenException(message);
    }
    return member;
  }
}
