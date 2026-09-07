package com.butingbe.domain.zoneevent.entity;

/**
 * 참여 상태 머신.
 *
 * <p>JOINED(참여 시작) → SUBMITTED(제출) → SUCCESS(성공) / UNDER_REVIEW(검수) → SUCCESS/FAIL. SUCCESS는 어뷰징 시
 * REVOKED로 회수될 수 있다. JOINED/SUBMITTED/UNDER_REVIEW가 "열린" 상태이며 유저·이벤트당 하나만 허용된다.
 */
public enum ParticipationStatus {
  JOINED,
  SUBMITTED,
  UNDER_REVIEW,
  SUCCESS,
  FAIL,
  CANCELLED,
  REVOKED;

  /** 아직 완료되지 않아 새 참여를 막는 열린 상태인지. */
  public boolean isOpen() {
    return this == JOINED || this == SUBMITTED || this == UNDER_REVIEW;
  }
}
