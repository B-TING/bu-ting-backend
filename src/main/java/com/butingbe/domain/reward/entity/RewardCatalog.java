package com.butingbe.domain.reward.entity;

import com.butingbe.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 보상 카탈로그. 배지는 code로 식별하고, 실물형(COUPON/GIFTICON)은 재고·월 캡·유효기간을 가진다. */
@Entity
@Table(name = "reward_catalog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RewardCatalog extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "reward_id", nullable = false, updatable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "reward_type", nullable = false, length = 20)
  private RewardType rewardType;

  @Column(nullable = false, length = 100, unique = true)
  private String code;

  @Column(nullable = false)
  private String name;

  @Column(name = "point_amount")
  private Integer pointAmount;

  @Column(name = "image_file_key", length = 512)
  private String imageFileKey;

  @Column private Integer stock;

  @Column(name = "monthly_cap")
  private Integer monthlyCap;

  @Column(name = "valid_days")
  private Integer validDays;

  @Column(nullable = false)
  private Boolean active;

  @Builder
  private RewardCatalog(
      RewardType rewardType,
      String code,
      String name,
      Integer pointAmount,
      String imageFileKey,
      Integer stock,
      Integer monthlyCap,
      Integer validDays,
      Boolean active) {
    this.rewardType = rewardType;
    this.code = code;
    this.name = name;
    this.pointAmount = pointAmount;
    this.imageFileKey = imageFileKey;
    this.stock = stock;
    this.monthlyCap = monthlyCap;
    this.validDays = validDays;
    this.active = active == null ? Boolean.TRUE : active;
  }
}
