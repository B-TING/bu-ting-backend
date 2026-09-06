package com.butingbe.domain.reward.repository;

import com.butingbe.domain.reward.entity.GrantReason;
import com.butingbe.domain.reward.entity.RewardGrant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RewardGrantRepository
    extends JpaRepository<RewardGrant, UUID>, JpaSpecificationExecutor<RewardGrant> {

  boolean existsByParticipationIdAndGrantReasonAndReward_Id(
      UUID participationId, GrantReason grantReason, UUID rewardId);

  List<RewardGrant> findByUserIdOrderByGrantedAtDesc(UUID userId);

  List<RewardGrant> findByParticipationIdInAndRevokedAtIsNull(
      java.util.Collection<UUID> participationIds);

  List<RewardGrant> findByParticipationIdAndRevokedAtIsNull(UUID participationId);

  long countByReward_IdAndGrantReasonAndGrantedAtGreaterThanEqual(
      UUID rewardId, GrantReason grantReason, OffsetDateTime from);
}
