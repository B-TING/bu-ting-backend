package com.butingbe.domain.zoneevent.entity;

/** 회차 수명 주기. SCHEDULED → OPEN → CLOSED → SETTLED(정산 완료). */
public enum RoundStatus {
  SCHEDULED,
  OPEN,
  CLOSED,
  SETTLED
}
