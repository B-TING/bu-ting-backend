package com.butingbe.domain.zoneevent.entity;

/** 인증 타겟(선택 장소) 수명 주기. 이벤트당 여러 타겟을 동시에 가질 수 있다(v2). */
public enum ZoneEventTargetStatus {
  ACTIVE,
  REPLACED,
  CANCELLED
}
