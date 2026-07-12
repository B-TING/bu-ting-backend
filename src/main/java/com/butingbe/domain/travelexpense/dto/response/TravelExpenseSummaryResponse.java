package com.butingbe.domain.travelexpense.dto.response;

import com.butingbe.domain.travelexpense.entity.ExpenseCategory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TravelExpenseSummaryResponse(
    UUID travelId,
    long expenseCount,
    List<CurrencySummary> currencySummaries,
    LocalDateTime from,
    LocalDateTime to) {

  public record CurrencySummary(
      String currency,
      long totalAmount,
      List<CategorySummary> categorySummaries,
      List<MemberSummary> memberSummaries) {}

  public record CategorySummary(
      ExpenseCategory category, long amount, long expenseCount, BigDecimal ratio) {}

  public record MemberSummary(
      UUID userId, String nickname, long paidAmount, long shareAmount, long balance) {}
}
