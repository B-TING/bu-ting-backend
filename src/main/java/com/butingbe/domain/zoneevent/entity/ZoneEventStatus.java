package com.butingbe.domain.zoneevent.entity;

/** 이벤트 수명 주기. SCHEDULED → ACTIVE → CLOSED, 또는 CANCELLED로 종료. */
public enum ZoneEventStatus {
  SCHEDULED,
  ACTIVE,
  CLOSED,
  CANCELLED
}
