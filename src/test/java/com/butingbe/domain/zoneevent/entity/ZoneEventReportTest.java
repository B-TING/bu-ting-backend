package com.butingbe.domain.zoneevent.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ZoneEventReportTest {

  @Test
  @DisplayName("검수 도장을 찍으면 검수자·시각·사유가 남는다")
  void stampDecisionRecordsReviewer() {
    ZoneEventReport report =
        ZoneEventReport.builder()
            .participationId(UUID.randomUUID())
            .reporterId(UUID.randomUUID())
            .reasonCode(ReportReasonCode.NOT_ON_SITE)
            .build();
    UUID reviewerId = UUID.randomUUID();

    report.stampDecision(reviewerId, "현장 사진과 GPS가 일치해 신고를 기각합니다.");

    assertThat(report.getReviewedBy()).isEqualTo(reviewerId);
    assertThat(report.getReviewedAt()).isNotNull();
    assertThat(report.getDecisionNote()).isEqualTo("현장 사진과 GPS가 일치해 신고를 기각합니다.");
  }
}
