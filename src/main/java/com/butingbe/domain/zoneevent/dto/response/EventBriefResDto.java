package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 이력 항목이 참조하는 이벤트 요약. */
public record EventBriefResDto(
    String eventId,
    String title,
    String typeCode,
    ZoneRef zone,
    UUID roundId,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt) {

  public static EventBriefResDto from(ZoneEvent event) {
    return new EventBriefResDto(
        event.getId().toString(),
        event.getTitle(),
        event.getType().getTypeCode(),
        ZoneRef.from(event.getZoneId()),
        event.getRoundId(),
        event.getStartsAt(),
        event.endsAt());
  }
}
