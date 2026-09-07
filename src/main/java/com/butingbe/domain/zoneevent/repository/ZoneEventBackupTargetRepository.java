package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ZoneEventBackupTarget;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneEventBackupTargetRepository
    extends JpaRepository<ZoneEventBackupTarget, UUID> {

  List<ZoneEventBackupTarget> findByRound_Id(UUID roundId);
}
