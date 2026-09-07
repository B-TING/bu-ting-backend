package com.butingbe.domain.zoneevent.entity;

import static org.assertj.core.api.Assertions.assertThat;

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
}
