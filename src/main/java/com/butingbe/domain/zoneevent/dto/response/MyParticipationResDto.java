package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;

/** 이벤트 상세에 포함되는 로그인 사용자의 가장 최근 참여 요약. */
public record MyParticipationResDto(String participationId, String status, boolean canResubmit) {

  public static MyParticipationResDto of(
      ZoneEventParticipation participation, boolean canResubmit) {
    return new MyParticipationResDto(
        participation.getId().toString(), participation.getStatus().name(), canResubmit);
  }
}
