package com.butingbe.domain.reward.entity;

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
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 유저 쿠폰함. 실물형(COUPON/GIFTICON) 지급이 여기에 ISSUED로 담긴다. */
@Entity
@Table(name = "user_coupon")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCoupon {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "user_coupon_id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reward_id", nullable = false)
  private RewardCatalog reward;

  @Column(name = "grant_id", nullable = false)
  private UUID grantId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CouponStatus status;

  @Column(name = "issued_at", nullable = false)
  private OffsetDateTime issuedAt;

  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;

  @Column(name = "used_at")
  private OffsetDateTime usedAt;

  @Builder
  private UserCoupon(UUID userId, RewardCatalog reward, UUID grantId, OffsetDateTime expiresAt) {
    this.userId = userId;
    this.reward = reward;
    this.grantId = grantId;
    this.status = CouponStatus.ISSUED;
    this.issuedAt = OffsetDateTime.now();
    this.expiresAt = expiresAt;
  }

  /** 미사용 쿠폰을 회수한다(EXPIRED). 이미 사용됐으면 그대로 둔다. */
  public boolean revokeIfUnused() {
    if (status == CouponStatus.ISSUED) {
      status = CouponStatus.EXPIRED;
      return true;
    }
    return false;
  }
}
