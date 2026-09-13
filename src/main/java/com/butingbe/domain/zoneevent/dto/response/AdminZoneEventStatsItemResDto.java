package com.butingbe.domain.zoneevent.dto.response;

import java.util.UUID;

public record AdminZoneEventStatsItemResDto(
    String roundId,
    String slotId,
    String zoneId,
    String eventId,
    long joinedCount,
    long submittedCount,
    long submissionAttemptCount,
    long successCount,
    long failCount,
    long pendingReviewCount,
    double successRate,
    long openReportCount,
    long basePaidCount,
    long specialSentCount,
    TopContent topContent,
    long newTitleGrantCount) {

  public record TopContent(String participationId, long likeCount) {}

  /** 아직 이벤트가 배정되지 않은 슬롯(0값). */
  public static AdminZoneEventStatsItemResDto empty(UUID roundId, UUID slotId, String zoneId) {
    return new AdminZoneEventStatsItemResDto(
        roundId.toString(),
        slotId.toString(),
        zoneId,
        null,
        0,
        0,
        0,
        0,
        0,
        0,
        0.0,
        0,
        0,
        0,
        null,
        0);
  }
}
