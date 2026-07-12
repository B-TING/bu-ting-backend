package com.butingbe.domain.travelexpense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.entity.TravelStatus;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travelexpense.dto.request.TravelExpenseCreateRequest;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseCreateResponse;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseListResponse;
import com.butingbe.domain.travelexpense.entity.ExpenseCategory;
import com.butingbe.domain.travelexpense.entity.ExpenseSplitType;
import com.butingbe.domain.travelexpense.repository.TravelExpenseRepository;
import com.butingbe.domain.travelexpense.repository.TravelExpenseShareRepository;
import com.butingbe.domain.travelteam.entity.TravelMember;
import com.butingbe.domain.travelteam.entity.TravelTeamRole;
import com.butingbe.domain.travelteam.repository.TravelMemberRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.support.AbstractContainerTest;
import com.butingbe.global.error.exception.ForbiddenException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TravelExpenseServiceTest extends AbstractContainerTest {

  @Autowired private TravelExpenseService travelExpenseService;
  @Autowired private TravelRepository travelRepository;
  @Autowired private TravelMemberRepository travelMemberRepository;
  @Autowired private TravelExpenseRepository travelExpenseRepository;
  @Autowired private TravelExpenseShareRepository travelExpenseShareRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void createsExpenseAndDistributesRemainderInParticipantOrder() {
    User creator = saveUser("creator-expense");
    User second = saveUser("second-expense");
    User third = saveUser("third-expense");
    Travel travel = saveTravel();
    saveMember(travel, creator, TravelTeamRole.LEADER);
    saveMember(travel, second, TravelTeamRole.MEMBER);
    saveMember(travel, third, TravelTeamRole.MEMBER);

    TravelExpenseCreateResponse response =
        travelExpenseService.createEqualExpense(
            AuthenticatedUser.from(creator),
            travel.getId(),
            request(
                10_000L,
                creator,
                List.of(second.getId(), creator.getId(), third.getId())));

    assertThat(response.splitType()).isEqualTo(ExpenseSplitType.EQUAL);
    assertThat(response.currency()).isEqualTo("KRW");
    assertThat(response.shares())
        .extracting(TravelExpenseCreateResponse.ShareResponse::shareAmount)
        .containsExactly(3_334L, 3_333L, 3_333L);
    assertThat(response.shares()).extracting(share -> share.userId())
        .containsExactly(second.getId(), creator.getId(), third.getId());
    assertThat(response.shares().stream().mapToLong(share -> share.shareAmount()).sum())
        .isEqualTo(10_000L);
    assertThat(travelExpenseRepository.count()).isEqualTo(1);
    assertThat(travelExpenseShareRepository.count()).isEqualTo(3);
  }

  @Test
  void rejectsDuplicatedParticipantsWithoutSavingExpense() {
    User creator = saveUser("duplicate-expense");
    Travel travel = saveTravel();
    saveMember(travel, creator, TravelTeamRole.LEADER);

    TravelExpenseCreateRequest request =
        request(10_000L, creator, List.of(creator.getId(), creator.getId()));

    assertThatThrownBy(
            () ->
                travelExpenseService.createEqualExpense(
                    AuthenticatedUser.from(creator), travel.getId(), request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expense participants must not be duplicated.");
    assertThat(travelExpenseRepository.count()).isZero();
  }

  @Test
  void rejectsParticipantWhoIsNotTravelMember() {
    User creator = saveUser("member-expense");
    User outsider = saveUser("outsider-expense");
    Travel travel = saveTravel();
    saveMember(travel, creator, TravelTeamRole.LEADER);

    assertThatThrownBy(
            () ->
                travelExpenseService.createEqualExpense(
                    AuthenticatedUser.from(creator),
                    travel.getId(),
                    request(10_000L, creator, List.of(creator.getId(), outsider.getId()))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Participant is not a travel member.");
    assertThat(travelExpenseRepository.count()).isZero();
  }

  @Test
  void calculatesEqualSharesForAmountSmallerThanParticipantCount() {
    assertThat(TravelExpenseService.calculateEqualShares(2L, 3))
        .containsExactly(1L, 1L, 0L);
  }

  @Test
  void getsLatestExpensePageWithParticipantCount() {
    User creator = saveUser("list-creator");
    User member = saveUser("list-member");
    Travel travel = saveTravel();
    saveMember(travel, creator, TravelTeamRole.LEADER);
    saveMember(travel, member, TravelTeamRole.MEMBER);
    createExpense(
        creator,
        travel,
        "Lunch",
        ExpenseCategory.FOOD,
        LocalDateTime.of(2026, 7, 12, 12, 0),
        List.of(creator.getId(), member.getId()));
    createExpense(
        creator,
        travel,
        "Taxi",
        ExpenseCategory.TRANSPORT,
        LocalDateTime.of(2026, 7, 12, 20, 0),
        List.of(creator.getId()));

    TravelExpenseListResponse response =
        travelExpenseService.getExpenses(
            AuthenticatedUser.from(member),
            travel.getId(),
            null,
            null,
            null,
            null,
            PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "spentAt")));

    assertThat(response.totalElements()).isEqualTo(2);
    assertThat(response.totalPages()).isEqualTo(2);
    assertThat(response.content()).hasSize(1);
    assertThat(response.content().getFirst().title()).isEqualTo("Taxi");
    assertThat(response.content().getFirst().participantCount()).isEqualTo(1);
  }

  @Test
  void filtersExpensesByCategoryPeriodAndPayer() {
    User creator = saveUser("filter-creator");
    User member = saveUser("filter-member");
    Travel travel = saveTravel();
    saveMember(travel, creator, TravelTeamRole.LEADER);
    saveMember(travel, member, TravelTeamRole.MEMBER);
    createExpense(
        creator,
        travel,
        "Breakfast",
        ExpenseCategory.FOOD,
        LocalDateTime.of(2026, 7, 12, 8, 0),
        List.of(creator.getId(), member.getId()));
    createExpense(
        member,
        travel,
        "Dinner",
        ExpenseCategory.FOOD,
        LocalDateTime.of(2026, 7, 12, 19, 0),
        List.of(creator.getId(), member.getId()));

    TravelExpenseListResponse response =
        travelExpenseService.getExpenses(
            AuthenticatedUser.from(creator),
            travel.getId(),
            ExpenseCategory.FOOD,
            LocalDateTime.of(2026, 7, 12, 12, 0),
            LocalDateTime.of(2026, 7, 12, 23, 59),
            member.getId(),
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "spentAt")));

    assertThat(response.content()).extracting(item -> item.title()).containsExactly("Dinner");
    assertThat(response.content().getFirst().payer().userId()).isEqualTo(member.getId());
  }

  @Test
  void rejectsExpenseListRequestFromNonMember() {
    User member = saveUser("list-member-only");
    User outsider = saveUser("list-outsider");
    Travel travel = saveTravel();
    saveMember(travel, member, TravelTeamRole.LEADER);

    assertThatThrownBy(
            () ->
                travelExpenseService.getExpenses(
                    AuthenticatedUser.from(outsider),
                    travel.getId(),
                    null,
                    null,
                    null,
                    null,
                    PageRequest.of(0, 20)))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("User is not a travel member.");
  }

  private TravelExpenseCreateRequest request(
      long amount, User payer, List<java.util.UUID> participants) {
    return new TravelExpenseCreateRequest(
        "Dinner",
        amount,
        null,
        ExpenseCategory.FOOD,
        payer.getId(),
        participants,
        LocalDateTime.of(2026, 7, 12, 18, 30),
        "Team dinner");
  }

  private void createExpense(
      User payer,
      Travel travel,
      String title,
      ExpenseCategory category,
      LocalDateTime spentAt,
      List<java.util.UUID> participants) {
    travelExpenseService.createEqualExpense(
        AuthenticatedUser.from(payer),
        travel.getId(),
        new TravelExpenseCreateRequest(
            title,
            10_000L,
            "KRW",
            category,
            payer.getId(),
            participants,
            spentAt,
            null));
  }

  private User saveUser(String nickname) {
    return userRepository.save(
        User.builder()
            .email(nickname + "@example.com")
            .provider("google")
            .providerId("google-" + nickname)
            .name(new Name("Kim", "Tester"))
            .nickname(nickname)
            .role(UserRole.USER)
            .build());
  }

  private Travel saveTravel() {
    return travelRepository.save(
        Travel.builder()
            .title("Busan")
            .startDate(LocalDate.of(2026, 7, 12))
            .endDate(LocalDate.of(2026, 7, 14))
            .status(TravelStatus.IN_PROGRESS)
            .build());
  }

  private void saveMember(Travel travel, User user, TravelTeamRole role) {
    travelMemberRepository.save(
        TravelMember.builder().travel(travel).user(user).role(role).build());
  }
}
