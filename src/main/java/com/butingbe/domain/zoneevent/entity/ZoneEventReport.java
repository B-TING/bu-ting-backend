package com.butingbe.domain.zoneevent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공개 참여 신고. 신고자·참여당 하나(UK). 누적 시 참여가 자동 숨김된다. */
@Entity
@Table(
    name = "zone_event_report",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_zone_event_report_participation_reporter",
          columnNames = {"participation_id", "reporter_id"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneEventReport {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "report_id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "participation_id", nullable = false)
  private UUID participationId;

  @Column(name = "reporter_id", nullable = false)
  private UUID reporterId;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason_code", nullable = false, length = 20)
  private ReportReasonCode reasonCode;

  @Column(length = 500)
  private String memo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReportStatus status;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Builder
  private ZoneEventReport(
      UUID participationId, UUID reporterId, ReportReasonCode reasonCode, String memo) {
    this.participationId = participationId;
    this.reporterId = reporterId;
    this.reasonCode = reasonCode;
    this.memo = memo;
    this.status = ReportStatus.OPEN;
    this.createdAt = OffsetDateTime.now();
  }

  /** 검수 결과로 처리 상태를 바꾼다. */
  public void resolveAs(ReportStatus status) {
    this.status = status;
  }
}
