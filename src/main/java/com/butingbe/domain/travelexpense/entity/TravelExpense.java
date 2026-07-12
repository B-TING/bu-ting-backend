package com.butingbe.domain.travelexpense.entity;

import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "travel_expense")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelExpense {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "travel_id", nullable = false)
  private Travel travel;

  @Column(nullable = false, length = 50)
  private String title;

  @Column(nullable = false)
  private Long amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ExpenseCategory category;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "payer_user_id", nullable = false)
  private User payer;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by_user_id", nullable = false, updatable = false)
  private User createdBy;

  @Column(name = "spent_at", nullable = false)
  private LocalDateTime spentAt;

  @Column(length = 500)
  private String memo;

  @Enumerated(EnumType.STRING)
  @Column(name = "split_type", nullable = false, length = 20)
  private ExpenseSplitType splitType;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Builder
  public TravelExpense(
      Travel travel,
      String title,
      Long amount,
      String currency,
      ExpenseCategory category,
      User payer,
      User createdBy,
      LocalDateTime spentAt,
      String memo,
      ExpenseSplitType splitType) {
    this.travel = Objects.requireNonNull(travel, "Travel is required.");
    this.title = requireTitle(title);
    this.amount = requirePositiveAmount(amount);
    this.currency = normalizeCurrency(currency);
    this.category = Objects.requireNonNull(category, "Expense category is required.");
    this.payer = Objects.requireNonNull(payer, "Payer is required.");
    this.createdBy = Objects.requireNonNull(createdBy, "Expense creator is required.");
    this.spentAt = Objects.requireNonNull(spentAt, "Spent time is required.");
    this.memo = memo;
    this.splitType = Objects.requireNonNull(splitType, "Split type is required.");
  }

  private static String requireTitle(String title) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("Expense title is required.");
    }
    if (title.length() > 50) {
      throw new IllegalArgumentException("Expense title must be 50 characters or fewer.");
    }
    return title.trim();
  }

  private static Long requirePositiveAmount(Long amount) {
    if (amount == null || amount <= 0) {
      throw new IllegalArgumentException("Expense amount must be positive.");
    }
    return amount;
  }

  private static String normalizeCurrency(String currency) {
    String normalized = currency == null || currency.isBlank() ? "KRW" : currency.trim().toUpperCase();
    if (normalized.length() != 3) {
      throw new IllegalArgumentException("Currency must be a 3-letter code.");
    }
    return normalized;
  }
}
