package com.butingbe.domain.reward.repository;

import com.butingbe.domain.reward.entity.UserBadge;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBadgeRepository extends JpaRepository<UserBadge, UUID> {

  boolean existsByUserIdAndReward_Id(UUID userId, UUID rewardId);

  List<UserBadge> findByUserIdOrderByEarnedAtDesc(UUID userId);
}
