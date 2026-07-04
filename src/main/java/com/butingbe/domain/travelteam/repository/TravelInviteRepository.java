package com.butingbe.domain.travelteam.repository;

import com.butingbe.domain.travelteam.entity.TravelInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TravelInviteRepository extends JpaRepository<TravelInvite, UUID> {
    Optional<TravelInvite> findByToken(String token);
}
