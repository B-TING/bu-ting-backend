package com.butingbe.domain.reward.repository;

import com.butingbe.domain.reward.entity.GrantReason;
import com.butingbe.domain.reward.entity.RewardGrant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardGrantRepository extends JpaRepository<RewardGrant, UUID> {

  boolean existsByParticipationIdAndGrantReasonAndReward_Id(
      UUID participationId, GrantReason grantReason, UUID rewardId);

  List<RewardGrant> findByUserIdOrderByGrantedAtDesc(UUID userId);
}
