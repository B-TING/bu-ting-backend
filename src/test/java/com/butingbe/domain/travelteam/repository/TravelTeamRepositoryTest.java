package com.butingbe.domain.travelteam.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.entity.TravelStatus;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travelteam.entity.TravelMember;
import com.butingbe.domain.travelteam.entity.TravelTeamRole;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.support.AbstractContainerTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TravelTeamRepositoryTest extends AbstractContainerTest {

  @Autowired private TravelRepository travelRepository;
  @Autowired private TravelMemberRepository travelMemberRepository;
  @Autowired private UserRepository userRepository;

  @Test
  @DisplayName("find members by travel id returns leader first")
  void findMembersByTravelId() {
    User member = userRepository.save(createUser("member-list-repo@example.com", "member-list"));
    User leader = userRepository.save(createUser("leader-list-repo@example.com", "leader-list"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    travelMemberRepository.save(
        TravelMember.builder().travel(travel).user(member).role(TravelTeamRole.MEMBER).build());
    travelMemberRepository.save(
        TravelMember.builder().travel(travel).user(leader).role(TravelTeamRole.LEADER).build());

    assertThat(travelMemberRepository.findMembersByTravelId(travel.getId()))
        .extracting(travelMember -> travelMember.getUser().getNickname())
        .containsExactly("leader-list", "member-list");
  }

  @Test
  @DisplayName("find travels by user id returns joined travels")
  void findTravelByUserId() {
    User user = userRepository.save(createUser("member@example.com", "member"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    travelMemberRepository.save(
        TravelMember.builder().travel(travel).user(user).role(TravelTeamRole.MEMBER).build());

    assertThat(travelMemberRepository.findTravelByUserId(user.getId()))
        .extracting(Travel::getId)
        .containsExactly(travel.getId());
  }

  @Test
  @DisplayName("exists by role checks travel leader")
  void existsByTravelUserAndRole() {
    User leader = userRepository.save(createUser("leader@example.com", "leader"));
    Travel travel = travelRepository.save(createTravel("Busan"));
    travelMemberRepository.save(
        TravelMember.builder().travel(travel).user(leader).role(TravelTeamRole.LEADER).build());

    assertThat(
            travelMemberRepository.existsByTravel_IdAndUser_IdAndRole(
                travel.getId(), leader.getId(), TravelTeamRole.LEADER))
        .isTrue();
    assertThat(
            travelMemberRepository.existsByTravel_IdAndUser_IdAndRole(
                travel.getId(), leader.getId(), TravelTeamRole.MEMBER))
        .isFalse();
  }

  private Travel createTravel(String title) {
    return Travel.builder()
        .title(title)
        .startDate(LocalDate.of(2026, 8, 1))
        .endDate(LocalDate.of(2026, 8, 3))
        .status(TravelStatus.PLANNED)
        .build();
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
