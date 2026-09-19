package com.butingbe.domain.route.repository;

import com.butingbe.domain.route.entity.PlaceTravelTime;
import com.butingbe.domain.route.entity.PlaceTravelTimeId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceTravelTimeRepository
    extends JpaRepository<PlaceTravelTime, PlaceTravelTimeId> {}
