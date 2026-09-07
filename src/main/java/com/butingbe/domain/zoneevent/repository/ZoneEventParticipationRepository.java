package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZoneEventParticipationRepository
    extends JpaRepository<ZoneEventParticipation, UUID>,
        JpaSpecificationExecutor<ZoneEventParticipation> {

  List<ZoneEventParticipation> findByEvent_IdAndUserIdOrderByJoinedAtDesc(
      UUID eventId, UUID userId);

  Optional<ZoneEventParticipation> findByEvent_IdAndUserIdAndStatusIn(
      UUID eventId, UUID userId, Collection<ParticipationStatus> statuses);

  long countByEvent_IdAndUserIdAndStatus(UUID eventId, UUID userId, ParticipationStatus status);

  long countByEvent_Id(UUID eventId);

  long countByEvent_IdAndStatus(UUID eventId, ParticipationStatus status);

  List<ZoneEventParticipation> findByEvent_IdAndStatusIn(
      UUID eventId, java.util.Collection<ParticipationStatus> statuses);

  @Query(
      "SELECT COUNT(p) FROM ZoneEventParticipation p "
          + "WHERE p.userId = :userId AND p.event.zoneId = :zoneId "
          + "AND p.status = com.butingbe.domain.zoneevent.entity.ParticipationStatus.SUCCESS")
  long countSuccessByUserAndZone(@Param("userId") UUID userId, @Param("zoneId") String zoneId);

  @Query(
      "SELECT p FROM ZoneEventParticipation p WHERE p.event.id = :eventId "
          + "AND p.status = com.butingbe.domain.zoneevent.entity.ParticipationStatus.SUCCESS "
          + "AND p.visibility = com.butingbe.domain.zoneevent.entity.ParticipationVisibility.PUBLIC "
          + "AND p.hidden = false ORDER BY p.likeCount DESC, p.completedAt ASC")
  List<ZoneEventParticipation> findTopPublicSuccessByEvent(
      @Param("eventId") UUID eventId, org.springframework.data.domain.Pageable pageable);
}
