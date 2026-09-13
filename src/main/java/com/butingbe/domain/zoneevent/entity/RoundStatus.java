package com.butingbe.domain.zoneevent.entity;

/** 회차 수명 주기. DRAFT → SCHEDULED → ACTIVE → CLOSED(→SETTLED), 또는 CANCELLED로 종료. */
public enum RoundStatus {
  DRAFT,
  SCHEDULED,
  ACTIVE,
  CLOSED,
  CANCELLED,
  SETTLED
}
