package com.butingbe.domain.travel.repository;

import com.butingbe.domain.travel.entity.PlanPlace;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanPlaceRepository extends JpaRepository<PlanPlace, UUID> {}
