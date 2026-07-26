package com.butingbe.domain.travelrecord.repository;

import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travelrecord.entity.TravelRecordPlace;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelRecordPlaceRepository extends JpaRepository<TravelRecordPlace, UUID> {

  List<TravelRecordPlace> findByTravelRecordDay_IdOrderBySequenceAsc(UUID travelRecordDayId);

  List<TravelRecordPlace> findByProviderAndProviderPlaceId(
      PlaceProvider provider, String providerPlaceId);
}
