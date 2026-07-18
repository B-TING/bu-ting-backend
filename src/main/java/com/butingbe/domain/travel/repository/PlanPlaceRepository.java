package com.butingbe.domain.travel.repository;

import com.butingbe.domain.travel.entity.PlanPlace;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanPlaceRepository extends JpaRepository<PlanPlace, UUID> {

  List<PlanPlace> findByPlan_IdOrderBySequenceAsc(UUID planId);

  List<PlanPlace> findByPlan_IdAndSequenceGreaterThanOrderBySequenceAsc(
      UUID planId, Integer sequence);

  boolean existsByPlan_IdAndSequence(UUID planId, Integer sequence);

  Optional<PlanPlace> findTopByPlan_IdOrderBySequenceDesc(UUID planId);
}
