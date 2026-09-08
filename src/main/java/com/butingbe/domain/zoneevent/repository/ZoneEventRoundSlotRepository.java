package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneEventRoundSlotRepository extends JpaRepository<ZoneEventRoundSlot, UUID> {

  List<ZoneEventRoundSlot> findByRound_Id(UUID roundId);

  Optional<ZoneEventRoundSlot> findByRound_IdAndZoneId(UUID roundId, String zoneId);
}
