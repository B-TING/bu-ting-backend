package com.butingbe.domain.travelteam.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travelteam.dto.InviteVerificationResponse;
import com.butingbe.domain.travelteam.entity.TravelInvite;
import com.butingbe.domain.travelteam.entity.TravelMember;
import com.butingbe.domain.travelteam.entity.TravelTeamRole;
import com.butingbe.domain.travelteam.repository.TravelInviteRepository;
import com.butingbe.domain.travelteam.repository.TravelMemberRepository;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelTeamService {

  private final TravelInviteRepository travelInviteRepository;
  private final TravelMemberRepository travelMemberRepository;
  private final TravelRepository travelRepository;
  private final UserRepository userRepository;

  public InviteVerificationResponse verifyToken(String token) {
    TravelInvite invite = findUsableInvite(token);
    return InviteVerificationResponse.from(invite.getTravel(), true);
  }

  @Transactional
  public String createInviteLink(AuthenticatedUser authenticatedUser, UUID travelId) {
    validateLeader(authenticatedUser, travelId);

    Travel travel =
        travelRepository
            .findById(travelId)
            .orElseThrow(() -> new IllegalArgumentException("Travel not found."));

    String token = UUID.randomUUID().toString();
    OffsetDateTime expiredAt = OffsetDateTime.now().plusHours(24);

    TravelInvite travelInvite =
        TravelInvite.builder().travel(travel).token(token).expiredAt(expiredAt).build();

    travelInviteRepository.save(travelInvite);
    return "https://yourdomain.com/invite?token=" + token;
  }

  @Transactional
  public InviteVerificationResponse acceptInvite(AuthenticatedUser authenticatedUser, String token) {
    User user = findAuthenticatedUser(authenticatedUser);
    TravelInvite invite = findUsableInvite(token);
    Travel travel = invite.getTravel();

    if (travelMemberRepository.existsByTravel_IdAndUser_Id(travel.getId(), user.getId())) {
      throw new IllegalArgumentException("User already joined this travel.");
    }

    travelMemberRepository.save(
        TravelMember.builder().travel(travel).user(user).role(TravelTeamRole.MEMBER).build());
    invite.markUsed();

    return InviteVerificationResponse.from(travel, true);
  }

  private TravelInvite findUsableInvite(String token) {
    TravelInvite invite =
        travelInviteRepository
            .findByToken(token)
            .orElseThrow(() -> new IllegalArgumentException("Invalid invite link."));

    if (invite.isExpired()) {
      throw new IllegalArgumentException("Invite link has expired.");
    }

    if (Boolean.TRUE.equals(invite.getUsed())) {
      throw new IllegalArgumentException("Invite link has already been used.");
    }

    return invite;
  }

  private void validateLeader(AuthenticatedUser authenticatedUser, UUID travelId) {
    User user = findAuthenticatedUser(authenticatedUser);
    boolean leader =
        travelMemberRepository.existsByTravel_IdAndUser_IdAndRole(
            travelId, user.getId(), TravelTeamRole.LEADER);

    if (!leader) {
      throw new IllegalArgumentException("Only travel leaders can create invite links.");
    }
  }

  private User findAuthenticatedUser(AuthenticatedUser authenticatedUser) {
    if (authenticatedUser == null || authenticatedUser.id() == null) {
      throw new UnauthenticatedException();
    }

    return userRepository
        .findById(authenticatedUser.id())
        .orElseThrow(UnauthenticatedException::new);
  }
}
