package com.butingbe.domain.zoneevent.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ZoneEventSubmissionTest {

  @Test
  @DisplayName("생성 직후 검수 대기 상태다")
  void startsUnderReview() {
    ZoneEventSubmission submission = submission();

    assertThat(submission.getReviewStatus()).isEqualTo(SubmissionReviewStatus.UNDER_REVIEW);
  }

  @Test
  @DisplayName("승인하면 SUCCESS로 바뀌고 검수자가 남는다")
  void approveMarksSuccess() {
    ZoneEventSubmission submission = submission();
    UUID reviewerId = UUID.randomUUID();

    submission.approve(reviewerId);

    assertThat(submission.getReviewStatus()).isEqualTo(SubmissionReviewStatus.SUCCESS);
    assertThat(submission.getReviewedBy()).isEqualTo(reviewerId);
    assertThat(submission.getReviewedAt()).isNotNull();
  }

  @Test
  @DisplayName("반려하면 REJECTED로 바뀌고 사유가 남는다")
  void rejectMarksRejected() {
    ZoneEventSubmission submission = submission();
    UUID reviewerId = UUID.randomUUID();

    submission.reject(reviewerId, "얼굴이 가려져 있어요");

    assertThat(submission.getReviewStatus()).isEqualTo(SubmissionReviewStatus.REJECTED);
    assertThat(submission.getRejectionReason()).isEqualTo("얼굴이 가려져 있어요");
  }

  private ZoneEventSubmission submission() {
    return ZoneEventSubmission.builder()
        .attemptNo(1)
        .placeName("해운대 해수욕장")
        .targetLatitude(35.1587)
        .targetLongitude(129.1604)
        .radiusM(100)
        .mediaFileKey("uploads/images/photo.jpg")
        .gpsLat(35.1587)
        .gpsLng(129.1604)
        .build();
  }
}
