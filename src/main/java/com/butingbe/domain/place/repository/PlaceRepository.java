package com.butingbe.domain.place.repository;

import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.place.entity.Place;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, UUID> {

  Optional<Place> findByProviderAndProviderPlaceId(String provider, String providerPlaceId);

  List<Place> findByZoneId(ChatZone zoneId);
}
