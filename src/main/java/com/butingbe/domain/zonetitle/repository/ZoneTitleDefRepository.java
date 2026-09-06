package com.butingbe.domain.zonetitle.repository;

import com.butingbe.domain.zonetitle.entity.ZoneTitleDef;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneTitleDefRepository extends JpaRepository<ZoneTitleDef, UUID> {

  List<ZoneTitleDef> findByZoneIdOrderByTierAsc(String zoneId);

  List<ZoneTitleDef> findAllByOrderByZoneIdAscTierAsc();
}
