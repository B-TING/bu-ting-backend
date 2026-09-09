package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import java.time.OffsetDateTime;

/** 관리자 전체 참여 목록 한 행. */
public record AdminParticipationListItemResDto(
    String participationId,
    String eventId,
    String roundId,
    String zoneId,
    String userId,
    String status,
    Boolean success,
    boolean hidden,
    OffsetDateTime joinedAt,
    OffsetDateTime completedAt) {

  public static AdminParticipationListItemResDto of(ZoneEventParticipation p) {
    return new AdminParticipationListItemResDto(
        p.getId().toString(),
        p.getEvent().getId().toString(),
        p.getEvent().getRoundId() == null ? null : p.getEvent().getRoundId().toString(),
        p.getEvent().getZoneId(),
        p.getUserId().toString(),
        p.getStatus().name(),
        p.getSuccess(),
        Boolean.TRUE.equals(p.getHidden()),
        p.getJoinedAt(),
        p.getCompletedAt());
  }
}
