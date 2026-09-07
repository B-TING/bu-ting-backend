package com.butingbe.domain.reward.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 포인트 잔액 캐시. 유저당 1행. 음수 잔액은 DB CHECK로 막는다(BR-07). */
@Entity
@Table(name = "user_point_balance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPointBalance {

  @Id
  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(nullable = false)
  private Integer balance;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Builder
  private UserPointBalance(UUID userId, Integer balance) {
    this.userId = userId;
    this.balance = balance == null ? 0 : balance;
    this.updatedAt = OffsetDateTime.now();
  }

  /** 잔액에 증감을 더한다. 결과가 음수면 0에서 멈춘다(BR-07). */
  public void add(int amount) {
    int next = balance + amount;
    balance = Math.max(next, 0);
    updatedAt = OffsetDateTime.now();
  }
}
