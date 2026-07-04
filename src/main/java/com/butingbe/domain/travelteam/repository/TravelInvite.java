package com.butingbe.domain.travelteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TravelInvite extends JpaRepository<TravelInvite, UUID> {
}
