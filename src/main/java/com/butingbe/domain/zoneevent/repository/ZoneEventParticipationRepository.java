package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneEventParticipationRepository
    extends JpaRepository<ZoneEventParticipation, UUID> {

  Optional<ZoneEventParticipation> findByEvent_IdAndUserIdAndStatusIn(
      UUID eventId, UUID userId, Collection<ParticipationStatus> statuses);

  long countByEvent_IdAndUserIdAndStatus(UUID eventId, UUID userId, ParticipationStatus status);
}
