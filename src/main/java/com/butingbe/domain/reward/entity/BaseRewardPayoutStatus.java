package com.butingbe.domain.reward.entity;

/** 기본 보상(성공 시 포인트·배지) 지급 확정 상태. 사진 승인과 별도로 진행된다. */
public enum BaseRewardPayoutStatus {
  PENDING_CONFIRM,
  CONFIRMED,
  PAID,
  FAILED
}
