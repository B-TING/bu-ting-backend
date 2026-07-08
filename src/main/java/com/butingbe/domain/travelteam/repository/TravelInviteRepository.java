package com.butingbe.domain.travelteam.repository;

import com.butingbe.domain.travelteam.entity.TravelInvite;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelInviteRepository extends JpaRepository<TravelInvite, UUID> {
  Optional<TravelInvite> findByToken(String token);

  Optional<TravelInvite> findFirstByTravel_IdAndUsedFalseAndExpiredAtAfterOrderByExpiredAtDesc(
      UUID travelId, OffsetDateTime now);

  long deleteByTravel_IdAndUsedFalse(UUID travelId);
}
