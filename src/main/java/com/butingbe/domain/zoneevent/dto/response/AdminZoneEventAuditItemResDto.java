package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import java.time.OffsetDateTime;
import java.util.Map;

public record AdminZoneEventAuditItemResDto(
    String auditId,
    String actorId,
    String action,
    String targetType,
    String targetId,
    Map<String, Object> detail,
    OffsetDateTime createdAt) {

  public static AdminZoneEventAuditItemResDto from(ZoneEventAuditLog log) {
    return new AdminZoneEventAuditItemResDto(
        log.getId().toString(),
        log.getActorId().toString(),
        log.getAction(),
        log.getTargetType(),
        log.getTargetId() == null ? null : log.getTargetId().toString(),
        log.getDetail(),
        log.getCreatedAt());
  }
}
