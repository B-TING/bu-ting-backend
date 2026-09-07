package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import java.time.OffsetDateTime;

/** 신고 접수 결과. */
public record ReportResDto(String reportId, String participationId, OffsetDateTime createdAt) {

  public static ReportResDto from(ZoneEventReport report) {
    return new ReportResDto(
        report.getId().toString(), report.getParticipationId().toString(), report.getCreatedAt());
  }
}
