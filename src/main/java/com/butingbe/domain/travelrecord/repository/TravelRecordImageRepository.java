package com.butingbe.domain.travelrecord.repository;

import com.butingbe.domain.travelrecord.entity.TravelRecordImage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelRecordImageRepository extends JpaRepository<TravelRecordImage, UUID> {

  List<TravelRecordImage> findByTravelRecord_IdOrderBySequenceAsc(UUID travelRecordId);
}
