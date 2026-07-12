package com.butingbe.domain.travel.repository;

import com.butingbe.domain.travel.entity.Plan;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

  List<Plan> findByTravel_IdOrderByDayNumberAsc(UUID travelId);

  boolean existsByTravel_IdAndDayNumber(UUID travelId, Integer dayNumber);
}
