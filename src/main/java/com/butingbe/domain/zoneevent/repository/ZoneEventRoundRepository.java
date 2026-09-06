package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneEventRoundRepository extends JpaRepository<ZoneEventRound, UUID> {

  List<ZoneEventRound> findByStatusAndStartsAtLessThanEqual(RoundStatus status, OffsetDateTime at);

  List<ZoneEventRound> findByStatusAndEndsAtLessThanEqual(RoundStatus status, OffsetDateTime at);
}
