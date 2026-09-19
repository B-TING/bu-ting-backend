package com.butingbe.domain.place.repository;

import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.place.entity.Place;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlaceRepository extends JpaRepository<Place, UUID> {

  Optional<Place> findByProviderAndProviderPlaceId(String provider, String providerPlaceId);

  List<Place> findByZoneId(ChatZone zoneId);

  /** 인기도 보강 대상. 아직 보강하지 않은 장소를 먼저, 그다음 오래된 순으로 고른다. */
  @Query("select p from Place p order by p.enrichedAt asc nulls first")
  List<Place> findEnrichmentTargets(Pageable pageable);
}
