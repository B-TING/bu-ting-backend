package com.butingbe.domain.place.repository;

import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.place.entity.Place;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, UUID> {

  Optional<Place> findByProviderAndProviderPlaceId(String provider, String providerPlaceId);

  List<Place> findByZoneId(ChatZone zoneId);

  /** 인기도 보강 대상. 아직 보강하지 않은 장소를 먼저, 그다음 오래된 순으로 고른다. */
  @Query("select p from Place p order by p.enrichedAt asc nulls first")
  List<Place> findEnrichmentTargets(Pageable pageable);

  /**
   * 일정 후보. 좌표가 있어야 배치할 수 있고, 리뷰가 많은 순으로 가져온다.
   *
   * <p>정확한 인기도 점수는 호출자가 계산한다. 여기서는 후보 풀을 좁히는 것까지만 한다.
   */
  @Query(
      "select p from Place p "
          + "where p.latitude is not null and p.longitude is not null "
          + "and (:zones is null or p.zoneId in :zones) "
          + "order by p.reviewCount desc nulls last, p.rating desc nulls last")
  List<Place> findCandidates(@Param("zones") Collection<ChatZone> zones, Pageable pageable);
}
