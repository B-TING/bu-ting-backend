package com.butingbe.domain.reward.repository;

import com.butingbe.domain.reward.entity.RewardPayout;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardPayoutRepository extends JpaRepository<RewardPayout, UUID> {

  List<RewardPayout> findByEventId(UUID eventId);

  boolean existsByParticipationId(UUID participationId);
}
