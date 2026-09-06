package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneEventRepository extends JpaRepository<ZoneEvent, UUID> {

  List<ZoneEvent> findByZoneIdAndStatusOrderByStartsAtAsc(String zoneId, ZoneEventStatus status);
}
