package com.butingbe.domain.travelexpense.service;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.travel.repository.TravelRepository;
import com.butingbe.domain.travelexpense.dto.request.TravelExpenseCreateRequest;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseCreateResponse;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseDetailResponse;
import com.butingbe.domain.travelexpense.dto.response.TravelExpenseListResponse;
import com.butingbe.domain.travelexpense.entity.ExpenseCategory;
import com.butingbe.domain.travelexpense.entity.ExpenseSplitType;
import com.butingbe.domain.travelexpense.entity.TravelExpense;
import com.butingbe.domain.travelexpense.entity.TravelExpenseShare;
import com.butingbe.domain.travelexpense.repository.TravelExpenseRepository;
import com.butingbe.domain.travelexpense.repository.TravelExpenseShareRepository;
import com.butingbe.domain.travelexpense.repository.TravelExpenseShareRepository.ExpenseParticipantCount;
import com.butingbe.domain.travelteam.entity.TravelMember;
import com.butingbe.domain.travelteam.entity.TravelTeamRole;
import com.butingbe.domain.travelteam.repository.TravelMemberRepository;
import com.butingbe.domain.travelteam.service.TravelMemberAuthorization;
import com.butingbe.domain.user.entity.User;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.time.LocalDateTime;
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
      UUID payerUserId,
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
            expenseSpecification(travelId, category, from, to, payerUserId), pageable);
    Map<UUID, Long> participantCounts = findParticipantCounts(expensePage.getContent());
    return TravelExpenseListResponse.of(expensePage, participantCounts);
  }

  @Transactional
  public TravelExpenseCreateResponse createEqualExpense(
      AuthenticatedUser authenticatedUser, UUID travelId, TravelExpenseCreateRequest request) {
    if (authenticatedUser == null || authenticatedUser.id() == null) {
      throw new UnauthenticatedException();
    }

    Travel travel =
        travelRepository
            .findById(travelId)
            .orElseThrow(() -> new ResourceNotFoundException("Travel not found."));
    TravelMember creator =
        travelMemberAuthorization.requireMember(travelId, authenticatedUser.id());

    validateDistinctParticipants(request.participantUserIds());
    Map<UUID, User> membersById = findMembersById(travelId);
    User payer = requireTravelMember(membersById, request.payerUserId(), "Payer");
    List<User> participants =
        request.participantUserIds().stream()
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

  private void validateDistinctParticipants(List<UUID> participantUserIds) {
    Set<UUID> uniqueIds = new HashSet<>(participantUserIds);
    if (uniqueIds.size() != participantUserIds.size()) {
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

  private void validateExpensePeriod(LocalDateTime from, LocalDateTime to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException("Expense search start time must not be after end time.");
    }
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

  private Specification<TravelExpense> expenseSpecification(
      UUID travelId,
      ExpenseCategory category,
      LocalDateTime from,
      LocalDateTime to,
      UUID payerUserId) {
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
    if (payerUserId != null) {
      specification =
          specification.and(
              (root, query, builder) ->
                  builder.equal(root.get("payer").get("id"), payerUserId));
    }
    return specification;
  }
}
