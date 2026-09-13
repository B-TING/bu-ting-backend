package com.butingbe.domain.zonetitle.repository;

import com.butingbe.domain.zonetitle.entity.ZoneTitleDef;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneTitleDefRepository extends JpaRepository<ZoneTitleDef, UUID> {

  List<ZoneTitleDef> findByZoneIdOrderByTierAsc(String zoneId);

  List<ZoneTitleDef> findAllByOrderByZoneIdAscTierAsc();

  boolean existsByZoneIdAndTier(String zoneId, Integer tier);

  boolean existsByZoneIdAndTierAndIdNot(String zoneId, Integer tier, UUID id);

  boolean existsByZoneIdAndRequiredSuccessCount(String zoneId, Integer requiredSuccessCount);

  boolean existsByZoneIdAndRequiredSuccessCountAndIdNot(
      String zoneId, Integer requiredSuccessCount, UUID id);
}
