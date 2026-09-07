package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneEventAuditLogRepository extends JpaRepository<ZoneEventAuditLog, UUID> {

  List<ZoneEventAuditLog> findByTargetTypeAndTargetId(String targetType, UUID targetId);
}
