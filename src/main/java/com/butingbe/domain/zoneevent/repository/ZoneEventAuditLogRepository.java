package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ZoneEventAuditLog;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZoneEventAuditLogRepository extends JpaRepository<ZoneEventAuditLog, UUID> {

  List<ZoneEventAuditLog> findByTargetTypeAndTargetId(String targetType, UUID targetId);

  @Query(
      value =
          "select a from ZoneEventAuditLog a where "
              + "(:targetType is null or a.targetType = :targetType) "
              + "and (:targetId is null or a.targetId = :targetId) "
              + "and (:actorId is null or a.actorId = :actorId) "
              + "and (cast(:from as OffsetDateTime) is null or a.createdAt >= :from) "
              + "and (cast(:to as OffsetDateTime) is null or a.createdAt <= :to) "
              + "order by a.createdAt desc",
      countQuery =
          "select count(a) from ZoneEventAuditLog a where "
              + "(:targetType is null or a.targetType = :targetType) "
              + "and (:targetId is null or a.targetId = :targetId) "
              + "and (:actorId is null or a.actorId = :actorId) "
              + "and (cast(:from as OffsetDateTime) is null or a.createdAt >= :from) "
              + "and (cast(:to as OffsetDateTime) is null or a.createdAt <= :to)")
  Page<ZoneEventAuditLog> searchForAdmin(
      @Param("targetType") String targetType,
      @Param("targetId") UUID targetId,
      @Param("actorId") UUID actorId,
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to,
      Pageable pageable);
}
