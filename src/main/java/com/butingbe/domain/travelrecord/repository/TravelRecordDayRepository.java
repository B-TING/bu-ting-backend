package com.butingbe.domain.travelrecord.repository;

import com.butingbe.domain.travelrecord.entity.TravelRecordDay;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelRecordDayRepository extends JpaRepository<TravelRecordDay, UUID> {

  List<TravelRecordDay> findByTravelRecord_IdOrderByDayNumberAsc(UUID travelRecordId);
}
