package com.butingbe.domain.travel.repository;

import com.butingbe.domain.travel.entity.Travel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TravelRepository extends JpaRepository<Travel, UUID> {
}
