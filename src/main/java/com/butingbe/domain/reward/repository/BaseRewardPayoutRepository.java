package com.butingbe.domain.reward.repository;

import com.butingbe.domain.reward.entity.BaseRewardPayout;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BaseRewardPayoutRepository extends JpaRepository<BaseRewardPayout, UUID> {

  Optional<BaseRewardPayout> findByParticipationId(UUID participationId);
}
