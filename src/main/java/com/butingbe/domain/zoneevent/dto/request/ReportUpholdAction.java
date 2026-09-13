package com.butingbe.domain.zoneevent.dto.request;

/**
 * 신고 인정 시 처리 방식. {@code HOLD}만 구현되어 있다 — 칭호 회수·실격·기지급 회수 범위는 별도 정책 확정 대상이라 {@code DISQUALIFY}는 아직
 * 지원하지 않는다(issue #243).
 */
public enum ReportUpholdAction {
  HOLD,
  DISQUALIFY
}
