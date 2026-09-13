package com.butingbe.domain.zoneevent.dto.response;

/** 신고 인정·기각 처리 결과. */
public record AdminZoneEventReportDecisionResDto(
    String reportId, String status, String participationId, Long revision) {}
