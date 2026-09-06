package com.butingbe.domain.zoneevent.repository;

import com.butingbe.domain.zoneevent.entity.ZoneEventLike;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneEventLikeRepository extends JpaRepository<ZoneEventLike, UUID> {

  boolean existsByParticipationIdAndUserId(UUID participationId, UUID userId);

  Optional<ZoneEventLike> findByParticipationIdAndUserId(UUID participationId, UUID userId);
}
