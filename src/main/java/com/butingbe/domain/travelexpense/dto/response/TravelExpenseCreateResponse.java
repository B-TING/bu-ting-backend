package com.butingbe.domain.travelexpense.dto.response;

import com.butingbe.domain.travelexpense.entity.ExpenseCategory;
import com.butingbe.domain.travelexpense.entity.ExpenseSplitType;
import com.butingbe.domain.travelexpense.entity.TravelExpense;
import com.butingbe.domain.travelexpense.entity.TravelExpenseShare;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TravelExpenseCreateResponse(
    UUID expenseId,
    UUID travelId,
    String title,
    Long amount,
    String currency,
    ExpenseCategory category,
    UUID payerUserId,
    UUID createdByUserId,
    ExpenseSplitType splitType,
    LocalDateTime spentAt,
    String memo,
    List<ShareResponse> shares) {

  public static TravelExpenseCreateResponse of(
      TravelExpense expense, List<TravelExpenseShare> shares) {
    return new TravelExpenseCreateResponse(
        expense.getId(),
        expense.getTravel().getId(),
        expense.getTitle(),
        expense.getAmount(),
        expense.getCurrency(),
        expense.getCategory(),
        expense.getPayer().getId(),
        expense.getCreatedBy().getId(),
        expense.getSplitType(),
        expense.getSpentAt(),
        expense.getMemo(),
        shares.stream().map(ShareResponse::from).toList());
  }

  public record ShareResponse(UUID userId, Long shareAmount) {

    private static ShareResponse from(TravelExpenseShare share) {
      return new ShareResponse(share.getUser().getId(), share.getShareAmount());
    }
  }
}
