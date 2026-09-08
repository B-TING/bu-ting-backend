package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import java.time.OffsetDateTime;

/** 검수 큐 항목. */
public record AdminReviewQueueItemResDto(
    String participationId,
    String eventId,
    String roundId,
    String zoneId,
    String userId,
    String currentSubmissionId,
    OffsetDateTime joinedAt) {

  public static AdminReviewQueueItemResDto of(ZoneEventParticipation p) {
    return new AdminReviewQueueItemResDto(
        p.getId().toString(),
        p.getEvent().getId().toString(),
        p.getEvent().getRoundId() == null ? null : p.getEvent().getRoundId().toString(),
        p.getEvent().getZoneId(),
        p.getUserId().toString(),
        p.getCurrentSubmissionId() == null ? null : p.getCurrentSubmissionId().toString(),
        p.getJoinedAt());
  }
}
