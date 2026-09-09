package com.butingbe.domain.zoneevent.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/** 관리자 신고 상세: 신고 내용·검수 대상·관련 지급 건. */
public record AdminZoneEventReportDetailResDto(
    String reportId,
    String participationId,
    String eventId,
    String roundId,
    String zoneId,
    String reporterId,
    String reasonCode,
    String memo,
    String status,
    String reviewedBy,
    OffsetDateTime reviewedAt,
    String decisionNote,
    Long revision,
    OffsetDateTime createdAt,
    List<AdminZoneEventReportPayoutResDto> payouts) {}
