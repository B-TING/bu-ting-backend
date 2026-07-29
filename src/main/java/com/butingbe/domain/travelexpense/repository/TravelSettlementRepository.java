package com.butingbe.domain.travelexpense.repository;

import com.butingbe.domain.travelexpense.entity.TravelSettlement;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelSettlementRepository extends JpaRepository<TravelSettlement, UUID> {

  boolean existsByTravel_Id(UUID travelId);

  @EntityGraph(attributePaths = {"travel", "confirmedBy"})
  Optional<TravelSettlement> findByTravel_Id(UUID travelId);
}
