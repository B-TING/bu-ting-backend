package com.butingbe.domain.travelexpense.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travelexpense.dto.request.TravelExpenseCreateRequest;
import com.butingbe.domain.travelexpense.dto.request.TravelExpenseUpdateRequest;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseCreateResponse;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseDetailResponse;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseListResponse;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseSummaryResponse;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseSummaryResponse.CategorySummary;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseSummaryResponse.CurrencySummary;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseSummaryResponse.MemberSummary;
import com.butingbe.domain.travelexpense.entity.ExpenseCategory;
import com.butingbe.domain.travelexpense.entity.ExpenseSplitType;
import com.butingbe.domain.travelexpense.entity.TravelExpense;
import com.butingbe.domain.travelexpense.entity.TravelExpenseShare;
import com.butingbe.domain.travelexpense.repository.TravelExpenseRepository;
import com.butingbe.domain.travelexpense.repository.TravelExpenseRepository.CategoryTotal;
import com.butingbe.domain.travelexpense.repository.TravelExpenseRepository.CurrencyTotal;
import com.butingbe.domain.travelexpense.repository.TravelExpenseRepository.MemberAmount;
import com.butingbe.domain.travelexpense.repository.TravelExpenseShareRepository;
import com.butingbe.domain.travelexpense.repository.TravelExpenseShareRepository.ExpenseParticipantCount;
import com.butingbe.domain.travelexpense.repository.TravelExpenseShareRepository.MemberShareAmount;
import com.butingbe.domain.travelexpense.repository.TravelSettlementRepository;
import com.butingbe.domain.travelteam.entity.TravelMember;
import com.butingbe.domain.travelteam.entity.TravelTeamRole;
import com.butingbe.domain.travelteam.repository.TravelMemberRepository;
import com.butingbe.domain.travelteam.service.TravelMemberAuthorization;
import com.butingbe.domain.user.entity.User;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelExpenseService {

  private final TravelRepository travelRepository;
  private final TravelMemberRepository travelMemberRepository;
  private final TravelExpenseRepository travelExpenseRepository;
  private final TravelExpenseShareRepository travelExpenseShareRepository;
  private final TravelMemberAuthorization travelMemberAuthorization;
  private final TravelSettlementRepository travelSettlementRepository;

  public TravelExpenseSummaryResponse getExpenseSummary(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      LocalDateTime from,
      LocalDateTime to) {
    if (authenticatedUser == null || authenticatedUser.id() == null) {
      throw new UnauthenticatedException();
    }
    lockTravel(travelId);
    travelMemberAuthorization.validateMember(travelId, authenticatedUser.id());
    validateExpensePeriod(from, to);

    List<CurrencyTotal> totals =
        travelExpenseRepository.summarizeCurrencies(travelId, from, to);
    List<CategoryTotal> categories =
        travelExpenseRepository.summarizeCategories(travelId, from, to);
    List<MemberAmount> paidAmounts =
        travelExpenseRepository.summarizePaidAmounts(travelId, from, to);
    List<MemberShareAmount> shareAmounts =
        travelExpenseShareRepository.summarizeShareAmounts(travelId, from, to);
    List<TravelMember> currentMembers = travelMemberRepository.findMembersByTravelId(travelId);

    List<CurrencySummary> currencySummaries =
        totals.stream()
            .map(
                total ->
                    toCurrencySummary(
                        total, categories, paidAmounts, shareAmounts, currentMembers))
            .toList();
    long expenseCount = totals.stream().mapToLong(CurrencyTotal::getExpenseCount).sum();
    return new TravelExpenseSummaryResponse(
        travelId, expenseCount, currencySummaries, from, to);
  }

  @Transactional
  public void deleteExpense(
      AuthenticatedUser authenticatedUser, UUID travelId, UUID expenseId) {
    if (authenticatedUser == null || authenticatedUser.id() == null) {
      throw new UnauthenticatedException();
    }
    lockTravel(travelId);
    TravelMember requester =
        travelMemberAuthorization.requireMember(travelId, authenticatedUser.id());
    TravelExpense expense =
        travelExpenseRepository
            .findByIdAndTravel_Id(expenseId, travelId)
            .orElseThrow(() -> new ResourceNotFoundException("Expense not found."));
    validateExpenseManager(
        requester,
        expense,
        authenticatedUser.id(),
        "Only the expense creator or travel leader can delete this expense.");
    validateSettlementOpen(travelId);

    travelExpenseShareRepository.deleteByExpense_Id(expenseId);
    travelExpenseShareRepository.flush();
    travelExpenseRepository.delete(expense);
    travelExpenseRepository.flush();
  }

  @Transactional
  public TravelExpenseDetailResponse updateExpense(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      UUID expenseId,
      TravelExpenseUpdateRequest request) {
    if (authenticatedUser == null || authenticatedUser.id() == null) {
      throw new UnauthenticatedException();
    }
    lockTravel(travelId);
    TravelMember requester =
        travelMemberAuthorization.requireMember(travelId, authenticatedUser.id());
    TravelExpense expense =
        travelExpenseRepository
            .findByIdAndTravel_Id(expenseId, travelId)
            .orElseThrow(() -> new ResourceNotFoundException("Expense not found."));
    validateExpenseManager(
        requester,
        expense,
        authenticatedUser.id(),
        "Only the expense creator or travel leader can modify this expense.");
    validateSettlementOpen(travelId);

    validateDistinctParticipants(request.participantIds());
    Map<UUID, User> membersById = findMembersById(travelId);
    User payer = requireTravelMember(membersById, request.payerId(), "Payer");
    List<User> participants =
        request.participantIds().stream()
            .map(userId -> requireTravelMember(membersById, userId, "Participant"))
            .toList();
    List<Long> amounts = calculateEqualShares(request.amount(), participants.size());

    expense.update(
        request.title(),
        request.amount(),
        request.currency(),
        request.category(),
        payer,
        request.spentAt(),
        request.memo());
    travelExpenseShareRepository.deleteByExpense_Id(expenseId);
    travelExpenseShareRepository.flush();

    List<TravelExpenseShare> shares =
        IntStream.range(0, participants.size())
            .mapToObj(
                index ->
                    TravelExpenseShare.builder()
                        .expense(expense)
                        .user(participants.get(index))
                        .shareAmount(amounts.get(index))
                        .build())
            .toList();
    travelExpenseShareRepository.saveAll(shares);
    travelExpenseRepository.flush();

    return TravelExpenseDetailResponse.of(expense, shares, true);
  }

  public TravelExpenseDetailResponse getExpense(
      AuthenticatedUser authenticatedUser, UUID travelId, UUID expenseId) {
    if (authenticatedUser == null || authenticatedUser.id() == null) {
      throw new UnauthenticatedException();
    }
    if (!travelRepository.existsById(travelId)) {
      throw new ResourceNotFoundException("Travel not found.");
    }
    TravelMember requester =
        travelMemberAuthorization.requireMember(travelId, authenticatedUser.id());
    TravelExpense expense =
        travelExpenseRepository
            .findByIdAndTravel_Id(expenseId, travelId)
            .orElseThrow(() -> new ResourceNotFoundException("Expense not found."));
    List<TravelExpenseShare> shares =
        travelExpenseShareRepository.findByExpense_IdOrderByIdAsc(expenseId);
    boolean editable =
        expense.getCreatedBy().getId().equals(authenticatedUser.id())
            || requester.getRole() == TravelTeamRole.LEADER;

    return TravelExpenseDetailResponse.of(expense, shares, editable);
  }

  public TravelExpenseListResponse getExpenses(
      AuthenticatedUser authenticatedUser,
      UUID travelId,
      ExpenseCategory category,
      LocalDateTime from,
      LocalDateTime to,
      UUID payerId,
      Pageable pageable) {
    if (authenticatedUser == null || authenticatedUser.id() == null) {
      throw new UnauthenticatedException();
    }
    if (!travelRepository.existsById(travelId)) {
      throw new ResourceNotFoundException("Travel not found.");
    }
    travelMemberAuthorization.validateMember(travelId, authenticatedUser.id());
    validateExpensePeriod(from, to);

    Page<TravelExpense> expensePage =
        travelExpenseRepository.findAll(
            expenseSpecification(travelId, category, from, to, payerId), pageable);
    Map<UUID, Long> participantCounts = findParticipantCounts(expensePage.getContent());
    return TravelExpenseListResponse.of(expensePage, participantCounts);
  }

  @Transactional
  public TravelExpenseCreateResponse createEqualExpense(
      AuthenticatedUser authenticatedUser, UUID travelId, TravelExpenseCreateRequest request) {
    if (authenticatedUser == null || authenticatedUser.id() == null) {
      throw new UnauthenticatedException();
    }

    Travel travel = lockTravel(travelId);
    TravelMember creator =
        travelMemberAuthorization.requireMember(travelId, authenticatedUser.id());
    validateSettlementOpen(travelId);

    validateDistinctParticipants(request.participantIds());
    Map<UUID, User> membersById = findMembersById(travelId);
    User payer = requireTravelMember(membersById, request.payerId(), "Payer");
    List<User> participants =
        request.participantIds().stream()
            .map(userId -> requireTravelMember(membersById, userId, "Participant"))
            .toList();

    TravelExpense expense =
        travelExpenseRepository.save(
            TravelExpense.builder()
                .travel(travel)
                .title(request.title())
                .amount(request.amount())
                .currency(request.currency())
                .category(request.category())
                .payer(payer)
                .createdBy(creator.getUser())
                .spentAt(request.spentAt())
                .memo(request.memo())
                .splitType(ExpenseSplitType.EQUAL)
                .build());

    List<Long> amounts = calculateEqualShares(request.amount(), participants.size());
    List<TravelExpenseShare> shares =
        IntStream.range(0, participants.size())
            .mapToObj(
                index ->
                    TravelExpenseShare.builder()
                        .expense(expense)
                        .user(participants.get(index))
                        .shareAmount(amounts.get(index))
                        .build())
            .toList();
    travelExpenseShareRepository.saveAll(shares);

    return TravelExpenseCreateResponse.of(expense, shares);
  }

  static List<Long> calculateEqualShares(long amount, int participantCount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("Expense amount must be positive.");
    }
    if (participantCount <= 0) {
      throw new IllegalArgumentException("At least one participant is required.");
    }

    long baseAmount = amount / participantCount;
    long remainder = amount % participantCount;
    return IntStream.range(0, participantCount)
        .mapToObj(index -> baseAmount + (index < remainder ? 1L : 0L))
        .toList();
  }

  private void validateDistinctParticipants(List<UUID> participantIds) {
    Set<UUID> uniqueIds = new HashSet<>(participantIds);
    if (uniqueIds.size() != participantIds.size()) {
      throw new IllegalArgumentException("Expense participants must not be duplicated.");
    }
  }

  private Map<UUID, User> findMembersById(UUID travelId) {
    Map<UUID, User> membersById = new LinkedHashMap<>();
    travelMemberRepository
        .findMembersByTravelId(travelId)
        .forEach(member -> membersById.put(member.getUser().getId(), member.getUser()));
    return membersById;
  }

  private User requireTravelMember(Map<UUID, User> membersById, UUID userId, String subject) {
    User member = membersById.get(userId);
    if (member == null) {
      throw new IllegalArgumentException(subject + " is not a travel member.");
    }
    return member;
  }

  private void validateExpenseManager(
      TravelMember requester, TravelExpense expense, UUID requesterId, String message) {
    boolean creator = expense.getCreatedBy().getId().equals(requesterId);
    boolean leader = requester.getRole() == TravelTeamRole.LEADER;
    if (!creator && !leader) {
      throw new ForbiddenException(message);
    }
  }

  private void validateExpensePeriod(LocalDateTime from, LocalDateTime to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException("Expense search start time must not be after end time.");
    }
  }

  private void validateSettlementOpen(UUID travelId) {
    if (travelSettlementRepository.existsByTravel_Id(travelId)) {
      throw new ConflictException("SETTLEMENT_CONFIRMED");
    }
  }

  private Travel lockTravel(UUID travelId) {
    return travelRepository
        .findByIdForUpdate(travelId)
        .orElseThrow(() -> new ResourceNotFoundException("Travel not found."));
  }

  private Map<UUID, Long> findParticipantCounts(List<TravelExpense> expenses) {
    if (expenses.isEmpty()) {
      return Map.of();
    }
    List<UUID> expenseIds = expenses.stream().map(TravelExpense::getId).toList();
    return travelExpenseShareRepository.countParticipantsByExpenseIds(expenseIds).stream()
        .collect(
            Collectors.toMap(
                ExpenseParticipantCount::getExpenseId,
                ExpenseParticipantCount::getParticipantCount));
  }

  private CurrencySummary toCurrencySummary(
      CurrencyTotal total,
      List<CategoryTotal> categories,
      List<MemberAmount> paidAmounts,
      List<MemberShareAmount> shareAmounts,
      List<TravelMember> currentMembers) {
    List<CategorySummary> categorySummaries =
        categories.stream()
            .filter(category -> category.getCurrency().equals(total.getCurrency()))
            .map(
                category ->
                    new CategorySummary(
                        category.getCategory(),
                        category.getAmount(),
                        category.getExpenseCount(),
                        calculateRatio(category.getAmount(), total.getTotalAmount())))
            .toList();

    Map<UUID, MemberAmounts> memberAmounts = new LinkedHashMap<>();
    currentMembers.forEach(
        member ->
            memberAmounts.put(
                member.getUser().getId(),
                new MemberAmounts(member.getUser().getId(), member.getUser().getNickname())));
    paidAmounts.stream()
        .filter(paid -> paid.getCurrency().equals(total.getCurrency()))
        .forEach(
            paid ->
                memberAmounts
                    .computeIfAbsent(
                        paid.getUserId(),
                        id -> new MemberAmounts(id, paid.getNickname()))
                    .paidAmount += paid.getAmount());
    shareAmounts.stream()
        .filter(share -> share.getCurrency().equals(total.getCurrency()))
        .forEach(
            share ->
                memberAmounts
                    .computeIfAbsent(
                        share.getUserId(),
                        id -> new MemberAmounts(id, share.getNickname()))
                    .shareAmount += share.getAmount());

    List<MemberSummary> memberSummaries =
        memberAmounts.values().stream()
            .sorted(
                Comparator.comparing((MemberAmounts member) -> member.nickname)
                    .thenComparing(member -> member.userId))
            .map(
                member ->
                    new MemberSummary(
                        member.userId,
                        member.nickname,
                        member.paidAmount,
                        member.shareAmount,
                        member.paidAmount - member.shareAmount))
            .toList();
    return new CurrencySummary(
        total.getCurrency(), total.getTotalAmount(), categorySummaries, memberSummaries);
  }

  private BigDecimal calculateRatio(long amount, long totalAmount) {
    if (totalAmount == 0) {
      return BigDecimal.ZERO.setScale(2);
    }
    return BigDecimal.valueOf(amount)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(totalAmount), 2, RoundingMode.HALF_UP);
  }

  private Specification<TravelExpense> expenseSpecification(
      UUID travelId,
      ExpenseCategory category,
      LocalDateTime from,
      LocalDateTime to,
      UUID payerId) {
    Specification<TravelExpense> specification =
        (root, query, builder) -> builder.equal(root.get("travel").get("id"), travelId);
    if (category != null) {
      specification =
          specification.and(
              (root, query, builder) -> builder.equal(root.get("category"), category));
    }
    if (from != null) {
      specification =
          specification.and(
              (root, query, builder) ->
                  builder.greaterThanOrEqualTo(root.get("spentAt"), from));
    }
    if (to != null) {
      specification =
          specification.and(
              (root, query, builder) -> builder.lessThanOrEqualTo(root.get("spentAt"), to));
    }
    if (payerId != null) {
      specification =
          specification.and(
              (root, query, builder) ->
                  builder.equal(root.get("payer").get("id"), payerId));
    }
    return specification;
  }

  private static class MemberAmounts {

    private final UUID userId;
    private final String nickname;
    private long paidAmount;
    private long shareAmount;

    private MemberAmounts(UUID userId, String nickname) {
      this.userId = userId;
      this.nickname = nickname;
    }
  }
}
