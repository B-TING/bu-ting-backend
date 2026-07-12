package com.butingbe.domain.travelexpense.dto.response;

import com.butingbe.domain.travelexpense.entity.ExpenseCategory;
import com.butingbe.domain.travelexpense.entity.ExpenseSplitType;
import com.butingbe.domain.travelexpense.entity.TravelExpense;
import com.butingbe.domain.travelexpense.entity.TravelExpenseShare;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TravelExpenseDetailResponse(
    UUID expenseId,
    UUID travelId,
    String title,
    Long amount,
    String currency,
    ExpenseCategory category,
    UserSummary payer,
    UserSummary createdBy,
    ExpenseSplitType splitType,
    LocalDateTime spentAt,
    String memo,
    List<ShareDetail> shares,
    boolean editable,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static TravelExpenseDetailResponse of(
      TravelExpense expense, List<TravelExpenseShare> shares, boolean editable) {
    return new TravelExpenseDetailResponse(
        expense.getId(),
        expense.getTravel().getId(),
        expense.getTitle(),
        expense.getAmount(),
        expense.getCurrency(),
        expense.getCategory(),
        new UserSummary(expense.getPayer().getId(), expense.getPayer().getNickname()),
        new UserSummary(expense.getCreatedBy().getId(), expense.getCreatedBy().getNickname()),
        expense.getSplitType(),
        expense.getSpentAt(),
        expense.getMemo(),
        shares.stream().map(ShareDetail::from).toList(),
        editable,
        expense.getCreatedAt(),
        expense.getUpdatedAt());
  }

  public record UserSummary(UUID userId, String nickname) {}

  public record ShareDetail(UUID userId, String nickname, Long shareAmount) {

    private static ShareDetail from(TravelExpenseShare share) {
      return new ShareDetail(
          share.getUser().getId(), share.getUser().getNickname(), share.getShareAmount());
    }
  }
}
