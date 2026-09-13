package com.butingbe.domain.reward.entity;

/** TOP_LIKE 특별 보상 지급 진행 상태. */
public enum RewardPayoutStatus {
  PENDING_ASSIGN,
  PENDING_CONFIRM,
  CONFIRMED,
  MAIL_SENT,
  INFO_COLLECTED,
  SENT,
  FAILED
}
