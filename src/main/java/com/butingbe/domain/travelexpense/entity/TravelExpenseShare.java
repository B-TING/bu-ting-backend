package com.butingbe.domain.travelexpense.entity;

import com.butingbe.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "travel_expense_share",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_travel_expense_share_expense_user",
          columnNames = {"expense_id", "user_id"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelExpenseShare {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "expense_id", nullable = false)
  private TravelExpense expense;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "share_amount", nullable = false)
  private Long shareAmount;

  @Builder
  public TravelExpenseShare(TravelExpense expense, User user, Long shareAmount) {
    this.expense = Objects.requireNonNull(expense, "Expense is required.");
    this.user = Objects.requireNonNull(user, "Expense participant is required.");
    if (shareAmount == null || shareAmount < 0) {
      throw new IllegalArgumentException("Share amount must not be negative.");
    }
    this.shareAmount = shareAmount;
  }
}
