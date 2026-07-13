package com.butingbe.domain.travelrecord.repository;

import com.butingbe.domain.travelrecord.entity.TravelRecordLike;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelRecordLikeRepository extends JpaRepository<TravelRecordLike, UUID> {

  boolean existsByUser_IdAndTravelRecord_Id(UUID userId, UUID travelRecordId);

  Optional<TravelRecordLike> findByUser_IdAndTravelRecord_Id(UUID userId, UUID travelRecordId);
}
