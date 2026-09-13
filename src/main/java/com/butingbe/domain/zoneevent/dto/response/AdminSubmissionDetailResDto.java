package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventSubmission;
import java.time.OffsetDateTime;

/** 검수 상세에 담기는 제출 시도 1건(현재 제출 또는 이력 항목 공용). */
public record AdminSubmissionDetailResDto(
    String submissionId,
    int attemptNo,
    String targetId,
    String placeName,
    Double targetLatitude,
    Double targetLongitude,
    Integer radiusM,
    String guideTextSnapshot,
    String mediaUrl,
    Double gpsLat,
    Double gpsLng,
    OffsetDateTime capturedAt,
    OffsetDateTime submittedAt,
    String reviewStatus,
    String rejectionReason,
    String reviewedBy,
    OffsetDateTime reviewedAt,
    long revision) {

  public static AdminSubmissionDetailResDto of(ZoneEventSubmission s, String mediaUrl) {
    return new AdminSubmissionDetailResDto(
        s.getId().toString(),
        s.getAttemptNo(),
        s.getTarget().getId().toString(),
        s.getPlaceName(),
        s.getTargetLatitude(),
        s.getTargetLongitude(),
        s.getRadiusM(),
        s.getGuideTextSnapshot(),
        mediaUrl,
        s.getGpsLat(),
        s.getGpsLng(),
        s.getCapturedAt(),
        s.getSubmittedAt(),
        s.getReviewStatus().name(),
        s.getRejectionReason(),
        s.getReviewedBy() == null ? null : s.getReviewedBy().toString(),
        s.getReviewedAt(),
        s.getRevision());
  }
}
