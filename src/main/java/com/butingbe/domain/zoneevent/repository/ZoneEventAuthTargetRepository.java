package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneEventAuthTargetRepository extends JpaRepository<ZoneEventAuthTarget, UUID> {

  Optional<ZoneEventAuthTarget> findByEvent_Id(UUID eventId);
}
