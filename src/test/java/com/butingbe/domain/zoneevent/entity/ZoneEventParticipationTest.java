package com.butingbe.domain.zoneevent.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ZoneEventParticipationTest {

  @Test
  @DisplayName("제출 이력을 연결하면 currentSubmissionId가 갱신된다")
  void linkSubmissionUpdatesCurrentSubmissionId() {
    ZoneEventParticipation participation =
        ZoneEventParticipation.join(null, UUID.randomUUID(), 35.1587, 129.1604);
    UUID submissionId = UUID.randomUUID();

    participation.linkSubmission(submissionId);

    assertThat(participation.getCurrentSubmissionId()).isEqualTo(submissionId);
  }

  @Test
  @DisplayName("반려 후 재제출하면 이전 시도의 completedAt·failReason이 지워진다")
  void resubmitClearsStaleCompletionFields() {
    ZoneEventParticipation participation =
        ZoneEventParticipation.join(null, UUID.randomUUID(), 35.1587, 129.1604);
    participation.markFail("NOT_ON_SITE");
    assertThat(participation.getCompletedAt()).isNotNull();
    assertThat(participation.getFailReason()).isEqualTo("NOT_ON_SITE");

    participation.submit(
        "uploads/images/retry.jpg", "재시도", 35.1587, 129.1604, OffsetDateTime.now());

    assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.SUBMITTED);
    assertThat(participation.getCompletedAt()).isNull();
    assertThat(participation.getFailReason()).isNull();
  }
}
