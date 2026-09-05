package com.butingbe.domain.travelexpense.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.user.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
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
                TravelExpenseShare.builder().expense(expense).user(payer).shareAmount(-1L).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Share amount must not be negative.");
  }

  @Test
  @DisplayName("제목이 비었거나 50자를 넘으면 경비를 만들 수 없다")
  void rejectsInvalidTitle() {
    assertThatThrownBy(() -> createExpense(10000L, "KRW", "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expense title is required.");
    assertThatThrownBy(() -> createExpense(10000L, "KRW", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expense title is required.");
    assertThatThrownBy(() -> createExpense(10000L, "KRW", "a".repeat(51)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Expense title must be 50 characters or fewer.");
  }

  @Test
  @DisplayName("통화 코드가 3글자가 아니면 경비를 만들 수 없다")
  void rejectsInvalidCurrencyCode() {
    assertThatThrownBy(() -> createExpense(10000L, "KRWW"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Currency must be a 3-letter code.");
  }

  @Test
  @DisplayName("통화를 비워두면 KRW로 정규화한다")
  void defaultsCurrencyToKrw() {
    assertThat(createExpense(10000L, null).getCurrency()).isEqualTo("KRW");
    assertThat(createExpense(10000L, "  ").getCurrency()).isEqualTo("KRW");
    assertThat(createExpense(10000L, "usd").getCurrency()).isEqualTo("USD");
  }

  private TravelExpense createExpense(Long amount, String currency, String title) {
    return TravelExpense.builder()
        .travel(travel)
        .title(title)
        .amount(amount)
        .currency(currency)
        .category(ExpenseCategory.FOOD)
        .payer(payer)
        .createdBy(creator)
        .spentAt(LocalDateTime.of(2026, 7, 12, 18, 30))
        .splitType(ExpenseSplitType.EQUAL)
        .build();
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
