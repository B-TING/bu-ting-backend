package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ZoneEventRankingSnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneEventRankingSnapshotRepository
    extends JpaRepository<ZoneEventRankingSnapshot, UUID> {

  List<ZoneEventRankingSnapshot> findByEventIdAndVersionOrderByRankNAsc(
      UUID eventId, Integer version);

  int countByEventId(UUID eventId);
}
