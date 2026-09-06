package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZoneEventRoundRepository extends JpaRepository<ZoneEventRound, UUID> {

  List<ZoneEventRound> findByStatusAndStartsAtLessThanEqual(RoundStatus status, OffsetDateTime at);

  List<ZoneEventRound> findByStatusAndEndsAtLessThanEqual(RoundStatus status, OffsetDateTime at);

  List<ZoneEventRound> findTop2ByStartsAtLessThanOrderByStartsAtDesc(OffsetDateTime before);

  List<ZoneEventRound> findByStartsAtGreaterThanEqual(OffsetDateTime from);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM ZoneEventRound r WHERE r.id = :id")
  Optional<ZoneEventRound> findWithLockById(@Param("id") UUID id);
}
