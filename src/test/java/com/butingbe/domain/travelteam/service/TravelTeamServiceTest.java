package com.butingbe.domain.travelteam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.entity.TravelStatus;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travelteam.dto.InviteVerificationResponse;
import com.butingbe.domain.travelteam.entity.TravelInvite;
import com.butingbe.domain.travelteam.entity.TravelMember;
import com.butingbe.domain.travelteam.entity.TravelTeamRole;
import com.butingbe.domain.travelteam.repository.TravelInviteRepository;
import com.butingbe.domain.travelteam.repository.TravelMemberRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.support.AbstractContainerTest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TravelTeamServiceTest extends AbstractContainerTest {

  @Autowired private TravelTeamService travelTeamService;
  @Autowired private TravelRepository travelRepository;
  @Autowired private TravelMemberRepository travelMemberRepository;
  @Autowired private TravelInviteRepository travelInviteRepository;
  @Autowired private UserRepository userRepository;

  @Test
  @DisplayName("leader can create invite link")
  void createInviteLinkByLeader() {
    User leader = userRepository.save(createUser("leader-invite@example.com", "leader-invite"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    saveMember(travel, leader, TravelTeamRole.LEADER);

    String inviteLink = travelTeamService.createInviteLink(AuthenticatedUser.from(leader), travel.getId());
    String token = tokenFrom(inviteLink);

    assertThat(inviteLink).startsWith("https://yourdomain.com/invite?token=");
    assertThat(travelInviteRepository.findByToken(token)).isPresent();
  }

  @Test
  @DisplayName("member cannot create invite link")
  void createInviteLinkByMemberThrowsException() {
    User member = userRepository.save(createUser("member-invite@example.com", "member-invite"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    saveMember(travel, member, TravelTeamRole.MEMBER);

    assertThatThrownBy(
            () -> travelTeamService.createInviteLink(AuthenticatedUser.from(member), travel.getId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Only travel leaders can create invite links.");
  }

  @Test
  @DisplayName("verify invite token returns travel information")
  void verifyToken() {
    Travel travel = travelRepository.save(createTravel("Busan"));
    TravelInvite invite = saveInvite(travel, "verify-token", OffsetDateTime.now().plusHours(1));

    InviteVerificationResponse response = travelTeamService.verifyToken(invite.getToken());

    assertThat(response.travelId()).isEqualTo(travel.getId());
    assertThat(response.travelName()).isEqualTo("Busan");
    assertThat(response.valid()).isTrue();
  }

  @Test
  @DisplayName("accept invite creates travel member and marks invite used")
  void acceptInvite() {
    User user = userRepository.save(createUser("accept@example.com", "accept"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    TravelInvite invite = saveInvite(travel, "accept-token", OffsetDateTime.now().plusHours(1));

    InviteVerificationResponse response =
        travelTeamService.acceptInvite(AuthenticatedUser.from(user), invite.getToken());

    assertThat(response.travelId()).isEqualTo(travel.getId());
    assertThat(travelMemberRepository.existsByTravel_IdAndUser_Id(travel.getId(), user.getId()))
        .isTrue();
    assertThat(travelInviteRepository.findByToken(invite.getToken()).orElseThrow().getUsed()).isTrue();
  }

  @Test
  @DisplayName("accept invite rejects already joined user")
  void acceptInviteAlreadyJoinedThrowsException() {
    User user = userRepository.save(createUser("joined@example.com", "joined"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    saveMember(travel, user, TravelTeamRole.MEMBER);
    TravelInvite invite = saveInvite(travel, "joined-token", OffsetDateTime.now().plusHours(1));

    assertThatThrownBy(
            () -> travelTeamService.acceptInvite(AuthenticatedUser.from(user), invite.getToken()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("User already joined this travel.");
  }

  @Test
  @DisplayName("member can exit travel")
  void memberExitTravel() {
    User leader = userRepository.save(createUser("leader-exit@example.com", "leader-exit"));
    User member = userRepository.save(createUser("member-exit@example.com", "member-exit"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    saveMember(travel, leader, TravelTeamRole.LEADER);
    saveMember(travel, member, TravelTeamRole.MEMBER);

    travelTeamService.exitTravel(AuthenticatedUser.from(member), travel.getId());

    assertThat(travelMemberRepository.existsByTravel_IdAndUser_Id(travel.getId(), member.getId()))
        .isFalse();
    assertThat(travelRepository.existsById(travel.getId())).isTrue();
  }

  @Test
  @DisplayName("leader alone can exit and delete travel")
  void leaderAloneExitDeletesTravel() {
    User leader = userRepository.save(createUser("leader-alone@example.com", "leader-alone"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    saveMember(travel, leader, TravelTeamRole.LEADER);

    travelTeamService.exitTravel(AuthenticatedUser.from(leader), travel.getId());

    assertThat(travelRepository.existsById(travel.getId())).isFalse();
  }

  @Test
  @DisplayName("leader cannot exit while other members remain")
  void leaderExitWithMembersThrowsConflict() {
    User leader = userRepository.save(createUser("leader-conflict@example.com", "leader-conflict"));
    User member = userRepository.save(createUser("member-conflict@example.com", "member-conflict"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    saveMember(travel, leader, TravelTeamRole.LEADER);
    saveMember(travel, member, TravelTeamRole.MEMBER);

    assertThatThrownBy(() -> travelTeamService.exitTravel(AuthenticatedUser.from(leader), travel.getId()))
        .isInstanceOf(ConflictException.class)
        .hasMessage("LEADER_TRANSFER_REQUIRED");
  }

  private Travel createTravel(String title) {
    return Travel.builder()
        .title(title)
        .startDate(LocalDate.of(2026, 8, 1))
        .endDate(LocalDate.of(2026, 8, 3))
        .status(TravelStatus.PLANNED)
        .build();
  }

  private TravelMember saveMember(Travel travel, User user, TravelTeamRole role) {
    return travelMemberRepository.save(
        TravelMember.builder().travel(travel).user(user).role(role).build());
  }

  private TravelInvite saveInvite(Travel travel, String token, OffsetDateTime expiredAt) {
    return travelInviteRepository.save(
        TravelInvite.builder().travel(travel).token(token).expiredAt(expiredAt).build());
  }

  private String tokenFrom(String inviteLink) {
    return inviteLink.substring(inviteLink.indexOf("token=") + "token=".length());
  }

  private User createUser(String email, String nickname) {
    return User.builder()
        .email(email)
        .provider("google")
        .providerId("google-" + nickname)
        .name(new Name("Kim", "Tester"))
        .nickname(nickname)
        .role(UserRole.USER)
        .build();
  }
}
