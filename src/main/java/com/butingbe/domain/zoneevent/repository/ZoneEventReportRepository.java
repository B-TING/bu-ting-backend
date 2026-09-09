package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ReportStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventReport;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZoneEventReportRepository extends JpaRepository<ZoneEventReport, UUID> {

  List<ReportStatus> UNRESOLVED_STATUSES = List.of(ReportStatus.OPEN, ReportStatus.REVIEWING);

  boolean existsByParticipationIdAndReporterId(UUID participationId, UUID reporterId);

  long countByParticipationId(UUID participationId);

  List<ZoneEventReport> findByParticipationId(UUID participationId);

  /** 미해결 신고(OPEN/REVIEWING) 존재 여부 — 수상자 선정·지급 보류 판단에 쓴다. */
  boolean existsByParticipationIdAndStatusIn(
      UUID participationId, Collection<ReportStatus> statuses);

  /** {@link #existsByParticipationIdAndStatusIn}을 {@link #UNRESOLVED_STATUSES}로 고정한 편의 메서드. */
  default boolean hasUnresolvedReports(UUID participationId) {
    return existsByParticipationIdAndStatusIn(participationId, UNRESOLVED_STATUSES);
  }

  @Query(
      value =
          "select r from ZoneEventReport r, ZoneEventParticipation p "
              + "where r.participationId = p.id "
              + "and (:status is null or r.status = :status) "
              + "and (:eventId is null or p.event.id = :eventId) "
              + "and (:roundId is null or p.event.roundId = :roundId) "
              + "and (:participationId is null or r.participationId = :participationId) "
              + "order by r.createdAt desc",
      countQuery =
          "select count(r) from ZoneEventReport r, ZoneEventParticipation p "
              + "where r.participationId = p.id "
              + "and (:status is null or r.status = :status) "
              + "and (:eventId is null or p.event.id = :eventId) "
              + "and (:roundId is null or p.event.roundId = :roundId) "
              + "and (:participationId is null or r.participationId = :participationId)")
  Page<ZoneEventReport> searchForAdmin(
      @Param("status") ReportStatus status,
      @Param("eventId") UUID eventId,
      @Param("roundId") UUID roundId,
      @Param("participationId") UUID participationId,
      Pageable pageable);
}
