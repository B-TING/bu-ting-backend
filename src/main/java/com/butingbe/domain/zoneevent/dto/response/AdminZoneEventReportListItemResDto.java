package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import java.time.OffsetDateTime;

/** 관리자 신고 목록 한 행. */
public record AdminZoneEventReportListItemResDto(
    String reportId,
    String participationId,
    String eventId,
    String roundId,
    String reporterId,
    String reasonCode,
    String status,
    OffsetDateTime createdAt) {

  public static AdminZoneEventReportListItemResDto of(
      ZoneEventReport report, ZoneEventParticipation participation) {
    return new AdminZoneEventReportListItemResDto(
        report.getId().toString(),
        report.getParticipationId().toString(),
        participation.getEvent().getId().toString(),
        participation.getEvent().getRoundId() == null
            ? null
            : participation.getEvent().getRoundId().toString(),
        report.getReporterId().toString(),
        report.getReasonCode().name(),
        report.getStatus().name(),
        report.getCreatedAt());
  }
}
