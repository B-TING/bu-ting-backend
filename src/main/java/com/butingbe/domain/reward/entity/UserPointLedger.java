package com.butingbe.domain.reward.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 포인트 증감 원장. 잔액 캐시({@link UserPointBalance})는 이 원장의 합과 항상 일치해야 한다. */
@Entity
@Table(name = "user_point_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPointLedger {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "ledger_id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private Integer amount;

  @Column(nullable = false, length = 30)
  private String reason;

  @Column(name = "grant_id")
  private UUID grantId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Builder
  private UserPointLedger(UUID userId, Integer amount, String reason, UUID grantId) {
    this.userId = userId;
    this.amount = amount;
    this.reason = reason;
    this.grantId = grantId;
    this.createdAt = OffsetDateTime.now();
  }
}
