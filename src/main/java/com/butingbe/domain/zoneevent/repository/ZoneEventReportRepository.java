package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ReportStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneEventReportRepository extends JpaRepository<ZoneEventReport, UUID> {

  boolean existsByParticipationIdAndReporterId(UUID participationId, UUID reporterId);

  long countByParticipationId(UUID participationId);

  List<ZoneEventReport> findByParticipationId(UUID participationId);

  /** 미해결 신고(OPEN/REVIEWING) 존재 여부 — 수상자 선정·지급 보류 판단에 쓴다. */
  boolean existsByParticipationIdAndStatusIn(
      UUID participationId, Collection<ReportStatus> statuses);
}
