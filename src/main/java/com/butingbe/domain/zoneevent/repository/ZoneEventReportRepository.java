package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneEventReportRepository extends JpaRepository<ZoneEventReport, UUID> {

  boolean existsByParticipationIdAndReporterId(UUID participationId, UUID reporterId);

  long countByParticipationId(UUID participationId);

  List<ZoneEventReport> findByParticipationId(UUID participationId);
}
