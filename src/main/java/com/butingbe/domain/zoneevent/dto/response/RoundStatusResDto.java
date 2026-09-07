package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.RoundStatus;
import java.time.OffsetDateTime;
import java.util.List;

/** 유저 회차 현황(FR-EVT-03): 회차 + 6구역 슬롯 상태(OPEN/REST/UPCOMING). */
public record RoundStatusResDto(
    String roundId,
    RoundStatus status,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    List<ZoneSlot> zones) {

  public record ZoneSlot(String zoneId, String slotStatus, String eventId) {}
}
