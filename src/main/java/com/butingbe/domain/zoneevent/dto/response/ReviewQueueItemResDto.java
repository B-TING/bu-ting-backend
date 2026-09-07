package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import java.time.OffsetDateTime;

/** 검수 큐 항목. 운영자 판단에 필요한 GPS·촬영 시각·신고 수를 담는다. */
public record ReviewQueueItemResDto(
    String participationId,
    String eventId,
    String zoneId,
    String userId,
    String status,
    Double gpsLat,
    Double gpsLng,
    OffsetDateTime capturedAt,
    boolean hidden,
    long reportCount,
    String mediaFileKey,
    OffsetDateTime joinedAt) {

  public static ReviewQueueItemResDto of(ZoneEventParticipation p, long reportCount) {
    return new ReviewQueueItemResDto(
        p.getId().toString(),
        p.getEvent().getId().toString(),
        p.getEvent().getZoneId(),
        p.getUserId().toString(),
        p.getStatus().name(),
        p.getGpsLat(),
        p.getGpsLng(),
        p.getCapturedAt(),
        Boolean.TRUE.equals(p.getHidden()),
        reportCount,
        p.getMediaFileKey(),
        p.getJoinedAt());
  }
}
