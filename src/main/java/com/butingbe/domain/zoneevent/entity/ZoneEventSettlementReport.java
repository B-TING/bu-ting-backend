package com.butingbe.domain.zoneevent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 회차 정산 리포트 스냅샷. 회차당 하나. */
@Entity
@Table(name = "zone_event_settlement_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneEventSettlementReport {

  @Id
  @Column(name = "round_id", nullable = false, updatable = false)
  private UUID roundId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> report;

  @Column(name = "generated_at", nullable = false)
  private OffsetDateTime generatedAt;

  @Builder
  private ZoneEventSettlementReport(UUID roundId, Map<String, Object> report) {
    this.roundId = roundId;
    this.report = report;
    this.generatedAt = OffsetDateTime.now();
  }
}
