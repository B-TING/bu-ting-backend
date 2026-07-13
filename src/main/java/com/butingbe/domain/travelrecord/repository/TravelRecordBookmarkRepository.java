package com.butingbe.domain.travelrecord.repository;

import com.butingbe.domain.travelrecord.entity.TravelRecordBookmark;
import com.butingbe.domain.travelrecord.entity.TravelRecordStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelRecordBookmarkRepository
    extends JpaRepository<TravelRecordBookmark, UUID> {

  boolean existsByUser_IdAndTravelRecord_Id(UUID userId, UUID travelRecordId);

  Optional<TravelRecordBookmark> findByUser_IdAndTravelRecord_Id(
      UUID userId, UUID travelRecordId);

  List<TravelRecordBookmark> findByUser_IdAndTravelRecord_StatusOrderByCreatedAtDesc(
      UUID userId, TravelRecordStatus status);
}
