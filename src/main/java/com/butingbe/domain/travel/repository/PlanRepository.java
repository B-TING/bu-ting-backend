package com.butingbe.domain.travel.repository;

import com.butingbe.domain.travel.entity.Plan;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, UUID> {}
