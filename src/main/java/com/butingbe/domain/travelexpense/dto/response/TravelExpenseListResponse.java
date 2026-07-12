package com.butingbe.domain.travelexpense.dto.response;

import com.butingbe.domain.travelexpense.entity.ExpenseCategory;
import com.butingbe.domain.travelexpense.entity.TravelExpense;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;

public record TravelExpenseListResponse(
    List<ExpenseSummary> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {

  public static TravelExpenseListResponse of(
      Page<TravelExpense> expensePage, Map<UUID, Long> participantCounts) {
    List<ExpenseSummary> content =
        expensePage.getContent().stream()
            .map(
                expense ->
                    ExpenseSummary.from(
                        expense, participantCounts.getOrDefault(expense.getId(), 0L)))
            .toList();
    return new TravelExpenseListResponse(
        content,
        expensePage.getNumber(),
        expensePage.getSize(),
        expensePage.getTotalElements(),
        expensePage.getTotalPages());
  }

  public record ExpenseSummary(
      UUID expenseId,
      String title,
      Long amount,
      String currency,
      ExpenseCategory category,
      PayerResponse payer,
      long participantCount,
      LocalDateTime spentAt,
      LocalDateTime createdAt) {

    private static ExpenseSummary from(TravelExpense expense, long participantCount) {
      return new ExpenseSummary(
          expense.getId(),
          expense.getTitle(),
          expense.getAmount(),
          expense.getCurrency(),
          expense.getCategory(),
          new PayerResponse(expense.getPayer().getId(), expense.getPayer().getNickname()),
          participantCount,
          expense.getSpentAt(),
          expense.getCreatedAt());
    }
  }

  public record PayerResponse(UUID userId, String nickname) {}
}
