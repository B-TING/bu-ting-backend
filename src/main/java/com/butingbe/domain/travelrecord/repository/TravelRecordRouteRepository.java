package com.butingbe.domain.travelrecord.repository;

import com.butingbe.domain.travelrecord.entity.TravelRecordRoute;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelRecordRouteRepository extends JpaRepository<TravelRecordRoute, UUID> {

  List<TravelRecordRoute> findByTravelRecordDay_Id(UUID travelRecordDayId);
}
