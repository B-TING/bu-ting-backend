package com.butingbe.domain.travelteam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.entity.TravelStatus;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travelteam.dto.InviteVerificationResponse;
import com.butingbe.domain.travelteam.dto.TravelInviteLinkInfoResponse;
import com.butingbe.domain.travelteam.dto.TravelMemberResponse;
import com.butingbe.domain.travelteam.dto.TravelTeamTravelResponse;
import com.butingbe.domain.travelteam.dto.request.TravelLeaderTransferRequest;
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
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.support.AbstractContainerTest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
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
  @DisplayName("user can get my travels filtered by status")
  void getMyTravelsByStatus() {
    User user = userRepository.save(createUser("my-travels@example.com", "my-travels"));
    Travel planned = travelRepository.save(createTravel("Planned", TravelStatus.PLANNED));
    Travel inProgress =
        travelRepository.save(createTravel("In Progress", TravelStatus.IN_PROGRESS));
    Travel completed = travelRepository.save(createTravel("Completed", TravelStatus.COMPLETED));
    saveMember(planned, user, TravelTeamRole.MEMBER);
    saveMember(inProgress, user, TravelTeamRole.LEADER);
    saveMember(completed, user, TravelTeamRole.MEMBER);

    List<TravelTeamTravelResponse> inProgressResponses =
        travelTeamService.getMyTravels(AuthenticatedUser.from(user), TravelStatus.IN_PROGRESS);
    List<TravelTeamTravelResponse> completedResponses =
        travelTeamService.getMyTravels(AuthenticatedUser.from(user), TravelStatus.COMPLETED);

    assertThat(inProgressResponses)
        .extracting(TravelTeamTravelResponse::travelId)
        .containsExactly(inProgress.getId());
    assertThat(inProgressResponses)
        .extracting(TravelTeamTravelResponse::role)
        .containsExactly(TravelTeamRole.LEADER);
    assertThat(completedResponses)
        .extracting(TravelTeamTravelResponse::travelId)
        .containsExactly(completed.getId());
  }

  @Test
  @DisplayName("user can get all my travels when status is omitted")
  void getMyTravelsWithoutStatus() {
    User user = userRepository.save(createUser("all-my-travels@example.com", "my-travels"));
    Travel planned =
        travelRepository.save(
            createTravel("Planned", TravelStatus.PLANNED, LocalDate.of(2026, 8, 1)));
    Travel completed =
        travelRepository.save(
            createTravel("Completed", TravelStatus.COMPLETED, LocalDate.of(2026, 9, 1)));
    saveMember(planned, user, TravelTeamRole.MEMBER);
    saveMember(completed, user, TravelTeamRole.MEMBER);

    List<TravelTeamTravelResponse> responses =
        travelTeamService.getMyTravels(AuthenticatedUser.from(user), null);

    assertThat(responses)
        .extracting(TravelTeamTravelResponse::travelId)
        .containsExactly(completed.getId(), planned.getId());
  }

  @Test
  @DisplayName("travel member can get travel members")
  void getTravelMembers() {
    User leader = userRepository.save(createUser("leader-list@example.com", "leader-list"));
    User member = userRepository.save(createUser("member-list@example.com", "member-list"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    saveMember(travel, leader, TravelTeamRole.LEADER);
    saveMember(travel, member, TravelTeamRole.MEMBER);

    List<TravelMemberResponse> responses =
        travelTeamService.getTravelMembers(AuthenticatedUser.from(member), travel.getId());

    assertThat(responses).hasSize(2);
    assertThat(responses)
        .extracting(TravelMemberResponse::role)
        .containsExactly(TravelTeamRole.LEADER, TravelTeamRole.MEMBER);
    assertThat(responses)
        .extracting(TravelMemberResponse::nickname)
        .containsExactly("leader-list", "member-list");
  }

  @Test
  @DisplayName("non travel member cannot get travel members")
  void getTravelMembersByNonMemberThrowsForbiddenException() {
    User leader = userRepository.save(createUser("leader-private@example.com", "leader-private"));
    User outsider =
        userRepository.save(createUser("outsider-private@example.com", "outsider-private"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    saveMember(travel, leader, TravelTeamRole.LEADER);

    assertThatThrownBy(
            () ->
                travelTeamService.getTravelMembers(
                    AuthenticatedUser.from(outsider), travel.getId()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("User is not a travel member.");
  }

  @Test
  @DisplayName("leader can remove member")
  void removeMember() {
    User leader = userRepository.save(createUser("leader-remove@example.com", "leader-remove"));
    User member = userRepository.save(createUser("member-remove@example.com", "member-remove"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    saveMember(travel, leader, TravelTeamRole.LEADER);
    saveMember(travel, member, TravelTeamRole.MEMBER);

    travelTeamService.removeMember(AuthenticatedUser.from(leader), travel.getId(), member.getId());

    assertThat(travelMemberRepository.existsByTravel_IdAndUser_Id(travel.getId(), member.getId()))
        .isFalse();
    assertThat(travelMemberRepository.existsByTravel_IdAndUser_Id(travel.getId(), leader.getId()))
        .isTrue();
  }

  @Test
  @DisplayName("member cannot remove member")
  void removeMemberByMemberThrowsForbiddenException() {
    User leader =
        userRepository.save(createUser("leader-remove-forbidden@example.com", "leader-remove"));
    User member =
        userRepository.save(createUser("member-remove-forbidden@example.com", "member-remove"));
    User target =
        userRepository.save(createUser("target-remove-forbidden@example.com", "target-remove"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    saveMember(travel, leader, TravelTeamRole.LEADER);
    saveMember(travel, member, TravelTeamRole.MEMBER);
    saveMember(travel, target, TravelTeamRole.MEMBER);

    assertThatThrownBy(
            () ->
                travelTeamService.removeMember(
                    AuthenticatedUser.from(member), travel.getId(), target.getId()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("Only travel leaders can remove members.");
  }

  @Test
  @DisplayName("leader cannot remove themselves")
  void removeSelfThrowsException() {
    User leader =
        userRepository.save(createUser("leader-remove-self@example.com", "leader-remove"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    saveMember(travel, leader, TravelTeamRole.LEADER);

    assertThatThrownBy(
            () ->
                travelTeamService.removeMember(
                    AuthenticatedUser.from(leader), travel.getId(), leader.getId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Leader cannot remove themselves.");
  }

  @Test
  @DisplayName("leader can transfer leader role to another member")
  void transferLeader() {
    User leader = userRepository.save(createUser("leader-transfer@example.com", "leader-transfer"));
    User member = userRepository.save(createUser("member-transfer@example.com", "member-transfer"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    saveMember(travel, leader, TravelTeamRole.LEADER);
    saveMember(travel, member, TravelTeamRole.MEMBER);

    travelTeamService.transferLeader(
        AuthenticatedUser.from(leader),
        travel.getId(),
        new TravelLeaderTransferRequest(member.getId()));

    assertThat(
            travelMemberRepository
                .findByTravel_IdAndUser_Id(travel.getId(), leader.getId())
                .orElseThrow()
                .getRole())
        .isEqualTo(TravelTeamRole.MEMBER);
    assertThat(
            travelMemberRepository
                .findByTravel_IdAndUser_Id(travel.getId(), member.getId())
                .orElseThrow()
                .getRole())
        .isEqualTo(TravelTeamRole.LEADER);
  }

  @Test
  @DisplayName("member cannot transfer leader role")
  void transferLeaderByMemberThrowsForbiddenException() {
    User leader =
        userRepository.save(createUser("leader-transfer-forbidden@example.com", "leader-transfer"));
    User member =
        userRepository.save(createUser("member-transfer-forbidden@example.com", "member-transfer"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    saveMember(travel, leader, TravelTeamRole.LEADER);
    saveMember(travel, member, TravelTeamRole.MEMBER);

    assertThatThrownBy(
            () ->
                travelTeamService.transferLeader(
                    AuthenticatedUser.from(member),
                    travel.getId(),
                    new TravelLeaderTransferRequest(leader.getId())))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("Only travel leaders can transfer leader role.");
  }

  @Test
  @DisplayName("leader cannot transfer leader role to non travel member")
  void transferLeaderToNonMemberThrowsException() {
    User leader =
        userRepository.save(createUser("leader-transfer-outsider@example.com", "leader-transfer"));
    User outsider =
        userRepository.save(
            createUser("outsider-transfer-outsider@example.com", "outsider-transfer"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    saveMember(travel, leader, TravelTeamRole.LEADER);

    assertThatThrownBy(
            () ->
                travelTeamService.transferLeader(
                    AuthenticatedUser.from(leader),
                    travel.getId(),
                    new TravelLeaderTransferRequest(outsider.getId())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("New leader is not a travel member.");
  }

  @Test
  @DisplayName("leader can get active invite link")
  void getInviteLinkByLeader() {
    User leader = userRepository.save(createUser("leader-get-invite@example.com", "leader"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    saveMember(travel, leader, TravelTeamRole.LEADER);
    saveInvite(travel, "expired-get-token", OffsetDateTime.now().minusMinutes(1));
    TravelInvite activeInvite =
        saveInvite(travel, "active-get-token", OffsetDateTime.now().plusHours(1));

    TravelInviteLinkInfoResponse response =
        travelTeamService.getInviteLink(AuthenticatedUser.from(leader), travel.getId());

    assertThat(response.inviteLink())
        .isEqualTo("https://yourdomain.com/invite?token=" + activeInvite.getToken());
    assertThat(response.expiredAt()).isEqualTo(activeInvite.getExpiredAt());
  }

  @Test
  @DisplayName("member cannot get invite link")
  void getInviteLinkByMemberThrowsException() {
    User member = userRepository.save(createUser("member-get-invite@example.com", "member"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    saveMember(travel, member, TravelTeamRole.MEMBER);

    assertThatThrownBy(
            () -> travelTeamService.getInviteLink(AuthenticatedUser.from(member), travel.getId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Only travel leaders can get invite links.");
  }

  @Test
  @DisplayName("leader can create invite link")
  void createInviteLinkByLeader() {
    User leader = userRepository.save(createUser("leader-invite@example.com", "leader-invite"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    saveMember(travel, leader, TravelTeamRole.LEADER);

    String inviteLink =
        travelTeamService.createInviteLink(AuthenticatedUser.from(leader), travel.getId());
    String token = tokenFrom(inviteLink);

    assertThat(inviteLink).startsWith("https://yourdomain.com/invite?token=");
    assertThat(travelInviteRepository.findByToken(token)).isPresent();
  }

  @Test
  @DisplayName("leader can delete unused invite links")
  void deleteInviteLinkByLeader() {
    User leader = userRepository.save(createUser("leader-delete-invite@example.com", "leader"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    saveMember(travel, leader, TravelTeamRole.LEADER);
    TravelInvite unusedInvite =
        saveInvite(travel, "unused-delete-token", OffsetDateTime.now().plusHours(1));
    TravelInvite usedInvite =
        saveInvite(travel, "used-delete-token", OffsetDateTime.now().plusHours(1));
    usedInvite.markUsed();

    travelTeamService.deleteInviteLink(AuthenticatedUser.from(leader), travel.getId());

    assertThat(travelInviteRepository.findById(unusedInvite.getId())).isEmpty();
    assertThat(travelInviteRepository.findById(usedInvite.getId())).isPresent();
  }

  @Test
  @DisplayName("member cannot delete invite links")
  void deleteInviteLinkByMemberThrowsException() {
    User member = userRepository.save(createUser("member-delete-invite@example.com", "member"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    saveMember(travel, member, TravelTeamRole.MEMBER);

    assertThatThrownBy(
            () ->
                travelTeamService.deleteInviteLink(AuthenticatedUser.from(member), travel.getId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Only travel leaders can delete invite links.");
  }

  @Test
  @DisplayName("member cannot create invite link")
  void createInviteLinkByMemberThrowsException() {
    User member = userRepository.save(createUser("member-invite@example.com", "member-invite"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    saveMember(travel, member, TravelTeamRole.MEMBER);

    assertThatThrownBy(
            () ->
                travelTeamService.createInviteLink(AuthenticatedUser.from(member), travel.getId()))
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
    assertThat(travelInviteRepository.findByToken(invite.getToken()).orElseThrow().getUsed())
        .isTrue();
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

    assertThatThrownBy(
            () -> travelTeamService.exitTravel(AuthenticatedUser.from(leader), travel.getId()))
        .isInstanceOf(ConflictException.class)
        .hasMessage("LEADER_TRANSFER_REQUIRED");
  }

  private Travel createTravel(String title) {
    return createTravel(title, TravelStatus.PLANNED);
  }

  private Travel createTravel(String title, TravelStatus status) {
    return createTravel(title, status, LocalDate.of(2026, 8, 1));
  }

  private Travel createTravel(String title, TravelStatus status, LocalDate startDate) {
    return Travel.builder()
        .title(title)
        .startDate(startDate)
        .endDate(startDate.plusDays(2))
        .status(status)
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
