package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventSubmission;
import java.time.OffsetDateTime;

/** 내 참여 이력 한 항목에 포함되는 제출 시도 1건. attemptNo 내림차순으로 나열된다. */
public record SubmissionHistoryItemResDto(
    String submissionId,
    int attemptNo,
    String targetId,
    String placeName,
    String mediaUrl,
    String reviewStatus,
    String rejectionReason,
    OffsetDateTime submittedAt,
    OffsetDateTime reviewedAt) {

  public static SubmissionHistoryItemResDto of(ZoneEventSubmission submission, String mediaUrl) {
    return new SubmissionHistoryItemResDto(
        submission.getId().toString(),
        submission.getAttemptNo(),
        submission.getTarget().getId().toString(),
        submission.getPlaceName(),
        mediaUrl,
        submission.getReviewStatus().name(),
        submission.getRejectionReason(),
        submission.getSubmittedAt(),
        submission.getReviewedAt());
  }
}
