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
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "travel_settlement_transfer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelSettlementTransfer {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "settlement_id", nullable = false, updatable = false)
  private TravelSettlement settlement;

  @Column(nullable = false, length = 3, updatable = false)
  private String currency;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "from_user_id", nullable = false, updatable = false)
  private User fromUser;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "to_user_id", nullable = false, updatable = false)
  private User toUser;

  @Column(nullable = false, updatable = false)
  private Long amount;

  @Builder
  public TravelSettlementTransfer(
      TravelSettlement settlement, String currency, User fromUser, User toUser, Long amount) {
    this.settlement = Objects.requireNonNull(settlement, "Settlement is required.");
    this.currency = Objects.requireNonNull(currency, "Currency is required.");
    this.fromUser = Objects.requireNonNull(fromUser, "Settlement sender is required.");
    this.toUser = Objects.requireNonNull(toUser, "Settlement receiver is required.");
    if (Objects.equals(fromUser.getId(), toUser.getId())) {
      throw new IllegalArgumentException("Settlement sender and receiver must be different.");
    }
    if (amount == null || amount <= 0) {
      throw new IllegalArgumentException("Settlement amount must be positive.");
    }
    this.amount = amount;
  }
}
