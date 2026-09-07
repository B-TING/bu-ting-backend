package com.butingbe.domain.reward.entity;

/** 보상 지급 보류 여부. 지급 단계(status)와 독립적으로 관리한다. */
public enum PayoutHoldStatus {
  NONE,
  HELD_REPORT
}
