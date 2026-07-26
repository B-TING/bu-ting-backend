package com.butingbe.domain.travelrecord.repository;

import com.butingbe.domain.travelrecord.entity.TravelRecordComment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelRecordCommentRepository extends JpaRepository<TravelRecordComment, UUID> {

  List<TravelRecordComment> findByTravelRecord_IdOrderByCreatedAtAsc(UUID travelRecordId);

  Optional<TravelRecordComment> findByIdAndTravelRecord_Id(UUID id, UUID travelRecordId);
}
