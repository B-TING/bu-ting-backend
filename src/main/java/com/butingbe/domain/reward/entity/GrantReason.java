package com.butingbe.domain.reward.entity;

/** 지급 사유. Phase 1은 BASE(성공 기본 보상)만 쓴다. TOP_LIKE·ZONE_WIN·TOP_RANK는 이후 Phase에서 추가된다. */
public enum GrantReason {
  BASE,
  TOP_LIKE,
  ZONE_WIN,
  TOP_RANK
}
