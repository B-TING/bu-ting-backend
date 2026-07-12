package com.butingbe.domain.travelexpense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.entity.TravelStatus;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travelexpense.dto.request.TravelExpenseCreateRequest;
import com.butingbe.domain.travelexpense.dto.request.TravelExpenseUpdateRequest;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseCreateResponse;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseSummaryResponse.MemberSummary;
import com.butingbe.domain.travelexpense.dto.response.TravelSettlementResponse;
import com.butingbe.domain.travelexpense.entity.ExpenseCategory;
import com.butingbe.domain.travelexpense.repository.TravelExpenseRepository;
import com.butingbe.domain.travelexpense.repository.TravelSettlementRepository;
import com.butingbe.domain.travelexpense.repository.TravelSettlementTransferRepository;
import com.butingbe.domain.travelteam.entity.TravelMember;
import com.butingbe.domain.travelteam.entity.TravelTeamRole;
import com.butingbe.domain.travelteam.repository.TravelMemberRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.support.AbstractContainerTest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TravelSettlementServiceTest extends AbstractContainerTest {

  @Autowired private TravelSettlementService travelSettlementService;
  @Autowired private TravelExpenseService travelExpenseService;
  @Autowired private TravelRepository travelRepository;
  @Autowired private TravelMemberRepository travelMemberRepository;
  @Autowired private TravelExpenseRepository travelExpenseRepository;
  @Autowired private TravelSettlementRepository travelSettlementRepository;
  @Autowired private TravelSettlementTransferRepository transferRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void calculatesDeterministicTransfersFromMemberBalances() {
    UUID creditorId = UUID.randomUUID();
    UUID firstDebtorId = UUID.randomUUID();
    UUID secondDebtorId = UUID.randomUUID();

    List<TravelSettlementResponse.Transfer> transfers =
        TravelSettlementService.calculateCurrencyTransfers(
            "KRW",
            List.of(
                new MemberSummary(creditorId, "creditor", 7_000L, 0L, 7_000L),
                new MemberSummary(firstDebtorId, "first", 0L, 4_000L, -4_000L),
                new MemberSummary(secondDebtorId, "second", 0L, 3_000L, -3_000L)));

    assertThat(transfers)
        .extracting(TravelSettlementResponse.Transfer::amount)
        .containsExactly(4_000L, 3_000L);
    assertThat(transfers)
        .extracting(TravelSettlementResponse.Transfer::toUserId)
        .containsOnly(creditorId);
  }

  @Test
  void memberCanPreviewSettlementAndLeaderCanConfirmIt() {
    TestTravel testTravel = createThreeMemberTravel("confirm");
    createExpense(testTravel, 9_000L);

    TravelSettlementResponse preview =
        travelSettlementService.getSettlement(
            AuthenticatedUser.from(testTravel.firstMember), testTravel.travel.getId());
    TravelSettlementResponse confirmed =
        travelSettlementService.confirmSettlement(
            AuthenticatedUser.from(testTravel.leader), testTravel.travel.getId());

    assertThat(preview.confirmed()).isFalse();
    assertThat(preview.transfers()).hasSize(2);
    assertThat(confirmed.confirmed()).isTrue();
    assertThat(confirmed.confirmedByUserId()).isEqualTo(testTravel.leader.getId());
    assertThat(confirmed.confirmedAt()).isNotNull();
    assertThat(confirmed.transfers()).hasSize(2);
    assertThat(travelSettlementRepository.existsByTravel_Id(testTravel.travel.getId())).isTrue();
    assertThat(transferRepository.count()).isEqualTo(2);
  }

  @Test
  void onlyLeaderCanConfirmSettlement() {
    TestTravel testTravel = createThreeMemberTravel("permission");
    createExpense(testTravel, 9_000L);

    assertThatThrownBy(
            () ->
                travelSettlementService.confirmSettlement(
                    AuthenticatedUser.from(testTravel.firstMember), testTravel.travel.getId()))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("Only the travel leader can confirm settlement.");
  }

  @Test
  void confirmedSettlementIsIdempotentAndReturnsStoredSnapshot() {
    TestTravel testTravel = createThreeMemberTravel("idempotent");
    createExpense(testTravel, 9_000L);

    TravelSettlementResponse first =
        travelSettlementService.confirmSettlement(
            AuthenticatedUser.from(testTravel.leader), testTravel.travel.getId());
    TravelSettlementResponse second =
        travelSettlementService.confirmSettlement(
            AuthenticatedUser.from(testTravel.leader), testTravel.travel.getId());

    assertThat(second).isEqualTo(first);
    assertThat(travelSettlementRepository.count()).isEqualTo(1);
  }

  @Test
  void blocksExpenseMutationsAfterSettlementConfirmation() {
    TestTravel testTravel = createThreeMemberTravel("locked");
    TravelExpenseCreateResponse expense = createExpense(testTravel, 9_000L);
    travelSettlementService.confirmSettlement(
        AuthenticatedUser.from(testTravel.leader), testTravel.travel.getId());

    assertSettlementConfirmed(
        () ->
            travelExpenseService.createEqualExpense(
                AuthenticatedUser.from(testTravel.leader),
                testTravel.travel.getId(),
                createRequest(testTravel, 3_000L)));
    assertSettlementConfirmed(
        () ->
            travelExpenseService.updateExpense(
                AuthenticatedUser.from(testTravel.leader),
                testTravel.travel.getId(),
                expense.expenseId(),
                updateRequest(testTravel)));
    assertSettlementConfirmed(
        () ->
            travelExpenseService.deleteExpense(
                AuthenticatedUser.from(testTravel.leader),
                testTravel.travel.getId(),
                expense.expenseId()));
    assertThat(travelExpenseRepository.existsById(expense.expenseId())).isTrue();
  }

  private void assertSettlementConfirmed(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOf(ConflictException.class)
        .hasMessage("SETTLEMENT_CONFIRMED");
  }

  private TravelExpenseCreateResponse createExpense(TestTravel testTravel, long amount) {
    return travelExpenseService.createEqualExpense(
        AuthenticatedUser.from(testTravel.leader),
        testTravel.travel.getId(),
        createRequest(testTravel, amount));
  }

  private TravelExpenseCreateRequest createRequest(TestTravel testTravel, long amount) {
    return new TravelExpenseCreateRequest(
        "Shared expense",
        amount,
        "KRW",
        ExpenseCategory.FOOD,
        testTravel.leader.getId(),
        List.of(
            testTravel.leader.getId(),
            testTravel.firstMember.getId(),
            testTravel.secondMember.getId()),
        LocalDateTime.of(2026, 7, 12, 18, 0),
        null);
  }

  private TravelExpenseUpdateRequest updateRequest(TestTravel testTravel) {
    return new TravelExpenseUpdateRequest(
        "Changed expense",
        12_000L,
        "KRW",
        ExpenseCategory.FOOD,
        testTravel.leader.getId(),
        List.of(
            testTravel.leader.getId(),
            testTravel.firstMember.getId(),
            testTravel.secondMember.getId()),
        LocalDateTime.of(2026, 7, 12, 19, 0),
        null);
  }

  private TestTravel createThreeMemberTravel(String suffix) {
    User leader = saveUser("leader-" + suffix);
    User firstMember = saveUser("first-" + suffix);
    User secondMember = saveUser("second-" + suffix);
    Travel travel =
        travelRepository.save(
            Travel.builder()
                .title("Busan")
                .startDate(LocalDate.of(2026, 7, 12))
                .endDate(LocalDate.of(2026, 7, 14))
                .status(TravelStatus.IN_PROGRESS)
                .build());
    saveMember(travel, leader, TravelTeamRole.LEADER);
    saveMember(travel, firstMember, TravelTeamRole.MEMBER);
    saveMember(travel, secondMember, TravelTeamRole.MEMBER);
    return new TestTravel(travel, leader, firstMember, secondMember);
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

  private void saveMember(Travel travel, User user, TravelTeamRole role) {
    travelMemberRepository.save(
        TravelMember.builder().travel(travel).user(user).role(role).build());
  }

  private record TestTravel(
      Travel travel, User leader, User firstMember, User secondMember) {}
}
