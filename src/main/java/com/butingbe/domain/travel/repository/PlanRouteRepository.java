package com.butingbe.domain.travel.repository;

import com.butingbe.domain.travel.entity.PlanRoute;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRouteRepository extends JpaRepository<PlanRoute, UUID> {

  List<PlanRoute> findByPlan_Id(UUID planId);

  void deleteByPlan_Id(UUID planId);
}
