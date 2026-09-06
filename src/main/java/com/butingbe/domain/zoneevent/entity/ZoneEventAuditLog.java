package com.butingbe.domain.zoneevent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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

/** 운영자 행위 추적(NFR-12). 추가 전용 로그. */
@Entity
@Table(name = "zone_event_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneEventAuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "audit_id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "actor_id", nullable = false)
  private UUID actorId;

  @Column(nullable = false, length = 40)
  private String action;

  @Column(name = "target_type", nullable = false, length = 40)
  private String targetType;

  @Column(name = "target_id")
  private UUID targetId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> detail;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Builder
  private ZoneEventAuditLog(
      UUID actorId, String action, String targetType, UUID targetId, Map<String, Object> detail) {
    this.actorId = actorId;
    this.action = action;
    this.targetType = targetType;
    this.targetId = targetId;
    this.detail = detail;
    this.createdAt = OffsetDateTime.now();
  }
}
