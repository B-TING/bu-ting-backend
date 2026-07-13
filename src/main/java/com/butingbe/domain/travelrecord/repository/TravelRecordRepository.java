package com.butingbe.domain.travelrecord.repository;

import com.butingbe.domain.travelrecord.entity.TravelRecord;
import com.butingbe.domain.travelrecord.entity.TravelRecordStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelRecordRepository extends JpaRepository<TravelRecord, UUID> {

  boolean existsByOriginalTravel_IdAndAuthor_Id(UUID travelId, UUID authorId);

  Optional<TravelRecord> findByOriginalTravel_IdAndAuthor_Id(UUID travelId, UUID authorId);

  List<TravelRecord> findByAuthor_IdOrderByCreatedAtDesc(UUID authorId);

  List<TravelRecord> findByStatusOrderByPublishedAtDescCreatedAtDesc(TravelRecordStatus status);
}
