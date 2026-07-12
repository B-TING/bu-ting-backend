package com.butingbe.domain.travelexpense.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.user.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TravelExpenseTest {

  private final Travel travel = Travel.builder().build();
  private final User payer = User.builder().build();
  private final User creator = User.builder().build();

  @Test
  void createsExpenseWithDefaultCurrency() {
    TravelExpense expense = createExpense(10_000L, null);

    assertThat(expense.getCurrency()).isEqualTo("KRW");
    assertThat(expense.getAmount()).isEqualTo(10_000L);
  }

  @Test
  void normalizesCurrencyCode() {
    TravelExpense expense = createExpense(10_000L, " usd ");

    assertThat(expense.getCurrency()).isEqualTo("USD");
  }

  @Test
  void rejectsNonPositiveExpenseAmount() {
    assertThatThrownBy(() -> createExpense(0L, "KRW"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expense amount must be positive.");
  }

  @Test
  void rejectsNegativeShareAmount() {
    TravelExpense expense = createExpense(10_000L, "KRW");

    assertThatThrownBy(
            () ->
                TravelExpenseShare.builder()
                    .expense(expense)
                    .user(payer)
                    .shareAmount(-1L)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Share amount must not be negative.");
  }

  private TravelExpense createExpense(Long amount, String currency) {
    return TravelExpense.builder()
        .travel(travel)
        .title("Dinner")
        .amount(amount)
        .currency(currency)
        .category(ExpenseCategory.FOOD)
        .payer(payer)
        .createdBy(creator)
        .spentAt(LocalDateTime.of(2026, 7, 12, 18, 30))
        .splitType(ExpenseSplitType.EQUAL)
        .build();
  }
}
