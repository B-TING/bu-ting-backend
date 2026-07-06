package com.butingbe.domain.travel.repository;

import com.butingbe.domain.travel.entity.Travel;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelRepository extends JpaRepository<Travel, UUID> {}
