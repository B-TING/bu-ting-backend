package com.butingbe.domain.travelteam.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travelteam.dto.InviteVerificationResponse;
import com.butingbe.domain.travelteam.dto.TravelMemberResponse;
import com.butingbe.domain.travelteam.dto.request.TravelLeaderTransferRequest;
import com.butingbe.domain.travelteam.entity.TravelInvite;
import com.butingbe.domain.travelteam.entity.TravelMember;
import com.butingbe.domain.travelteam.entity.TravelTeamRole;
import com.butingbe.domain.travelteam.repository.TravelInviteRepository;
import com.butingbe.domain.travelteam.repository.TravelMemberRepository;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.time.OffsetDateTime;
import java.util.List;
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

  public List<TravelMemberResponse> getTravelMembers(
      AuthenticatedUser authenticatedUser, UUID travelId) {
    User user = findAuthenticatedUser(authenticatedUser);
    validateTravelExists(travelId);
    validateTravelMember(travelId, user.getId());

    return travelMemberRepository.findMembersByTravelId(travelId).stream()
        .map(TravelMemberResponse::from)
        .toList();
  }

  @Transactional
  public void removeMember(AuthenticatedUser authenticatedUser, UUID travelId, UUID targetUserId) {
    User user = findAuthenticatedUser(authenticatedUser);
    validateTravelExists(travelId);

    TravelMember leader =
        travelMemberRepository
            .findByTravel_IdAndUser_Id(travelId, user.getId())
            .orElseThrow(() -> new ForbiddenException("User is not a travel member."));

    if (leader.getRole() != TravelTeamRole.LEADER) {
      throw new ForbiddenException("Only travel leaders can remove members.");
    }

    if (user.getId().equals(targetUserId)) {
      throw new IllegalArgumentException("Leader cannot remove themselves.");
    }

    TravelMember targetMember =
        travelMemberRepository
            .findByTravel_IdAndUser_Id(travelId, targetUserId)
            .orElseThrow(() -> new IllegalArgumentException("Target user is not a travel member."));

    if (targetMember.getRole() == TravelTeamRole.LEADER) {
      throw new IllegalArgumentException("Leader cannot be removed.");
    }

    travelMemberRepository.delete(targetMember);
  }

  @Transactional
  public void transferLeader(
      AuthenticatedUser authenticatedUser, UUID travelId, TravelLeaderTransferRequest request) {
    User user = findAuthenticatedUser(authenticatedUser);
    validateTravelExists(travelId);

    TravelMember currentLeader =
        travelMemberRepository
            .findByTravel_IdAndUser_Id(travelId, user.getId())
            .orElseThrow(() -> new ForbiddenException("User is not a travel member."));

    if (currentLeader.getRole() != TravelTeamRole.LEADER) {
      throw new ForbiddenException("Only travel leaders can transfer leader role.");
    }

    if (user.getId().equals(request.newLeaderUserId())) {
      throw new IllegalArgumentException("New leader must be another travel member.");
    }

    TravelMember newLeader =
        travelMemberRepository
            .findByTravel_IdAndUser_Id(travelId, request.newLeaderUserId())
            .orElseThrow(() -> new IllegalArgumentException("New leader is not a travel member."));

    currentLeader.changeRole(TravelTeamRole.MEMBER);
    newLeader.changeRole(TravelTeamRole.LEADER);
  }

  @Transactional
  public void deleteInviteLink(AuthenticatedUser authenticatedUser, UUID travelId) {
    validateLeader(authenticatedUser, travelId, "Only travel leaders can delete invite links.");
    validateTravelExists(travelId);

    travelInviteRepository.deleteByTravel_IdAndUsedFalse(travelId);
  }

  @Transactional
  public String createInviteLink(AuthenticatedUser authenticatedUser, UUID travelId) {
    validateLeader(authenticatedUser, travelId, "Only travel leaders can create invite links.");

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
  public InviteVerificationResponse acceptInvite(
      AuthenticatedUser authenticatedUser, String token) {
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

  @Transactional
  public void exitTravel(AuthenticatedUser authenticatedUser, UUID travelId) {
    User user = findAuthenticatedUser(authenticatedUser);
    TravelMember member =
        travelMemberRepository
            .findByTravel_IdAndUser_Id(travelId, user.getId())
            .orElseThrow(() -> new IllegalArgumentException("User is not a travel member."));

    long memberCount = travelMemberRepository.countByTravel_Id(travelId);

    if (member.getRole() == TravelTeamRole.MEMBER) {
      travelMemberRepository.delete(member);
      return;
    }

    if (memberCount == 1) {
      travelMemberRepository.delete(member);
      travelMemberRepository.flush();
      travelRepository.delete(member.getTravel());
      return;
    }

    throw new ConflictException("LEADER_TRANSFER_REQUIRED");
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

  private void validateLeader(AuthenticatedUser authenticatedUser, UUID travelId, String message) {
    User user = findAuthenticatedUser(authenticatedUser);
    boolean leader =
        travelMemberRepository.existsByTravel_IdAndUser_IdAndRole(
            travelId, user.getId(), TravelTeamRole.LEADER);

    if (!leader) {
      throw new IllegalArgumentException(message);
    }
  }

  private void validateTravelExists(UUID travelId) {
    if (!travelRepository.existsById(travelId)) {
      throw new IllegalArgumentException("Travel not found.");
    }
  }

  private void validateTravelMember(UUID travelId, UUID userId) {
    if (!travelMemberRepository.existsByTravel_IdAndUser_Id(travelId, userId)) {
      throw new ForbiddenException("User is not a travel member.");
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
