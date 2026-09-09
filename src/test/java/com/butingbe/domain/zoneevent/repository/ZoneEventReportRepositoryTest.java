package com.butingbe.domain.zoneevent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.zoneevent.entity.ReportReasonCode;
import com.butingbe.domain.zoneevent.entity.ReportStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import com.butingbe.support.AbstractContainerTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ZoneEventReportRepositoryTest extends AbstractContainerTest {

  @Autowired private ZoneEventReportRepository reportRepository;

  @Test
  @DisplayName("OPEN·REVIEWING 신고가 있으면 미해결로 본다")
  void unresolvedWhenOpenOrReviewing() {
    UUID participationId = UUID.randomUUID();
    ZoneEventReport report =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(participationId)
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.SPAM)
                .build());

    assertThat(reportRepository.hasUnresolvedReports(participationId)).isTrue();

    ReflectionTestUtils.setField(report, "status", ReportStatus.REVIEWING);
    reportRepository.saveAndFlush(report);
    assertThat(reportRepository.hasUnresolvedReports(participationId)).isTrue();
  }

  @Test
  @DisplayName("모든 신고가 UPHELD·DISMISSED면 미해결이 아니다")
  void resolvedWhenTerminal() {
    UUID participationId = UUID.randomUUID();
    ZoneEventReport report =
        reportRepository.save(
            ZoneEventReport.builder()
                .participationId(participationId)
                .reporterId(UUID.randomUUID())
                .reasonCode(ReportReasonCode.SPAM)
                .build());
    report.resolveAs(ReportStatus.DISMISSED);
    reportRepository.saveAndFlush(report);

    assertThat(reportRepository.hasUnresolvedReports(participationId)).isFalse();
  }

  @Test
  @DisplayName("신고가 없으면 미해결이 아니다")
  void noReportsIsResolved() {
    assertThat(reportRepository.hasUnresolvedReports(UUID.randomUUID())).isFalse();
  }
}
