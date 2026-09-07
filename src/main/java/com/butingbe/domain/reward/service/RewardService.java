package com.butingbe.domain.reward.service;

import com.butingbe.domain.reward.dto.response.BaseRewardResult;
import com.butingbe.domain.reward.dto.response.GrantedRewardDto;
import com.butingbe.domain.reward.entity.GrantReason;
import com.butingbe.domain.reward.entity.RewardCatalog;
import com.butingbe.domain.reward.entity.RewardGrant;
import com.butingbe.domain.reward.entity.UserBadge;
import com.butingbe.domain.reward.repository.RewardCatalogRepository;
import com.butingbe.domain.reward.repository.RewardGrantRepository;
import com.butingbe.domain.reward.repository.UserBadgeRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보상 지급.
 *
 * <p>참여가 성공으로 확정될 때 이벤트의 base_reward 스냅샷(포인트 수치 + 배지 코드)에 따라 지급한다. 참여를 UUID로만 참조하므로 zoneevent 도메인에
 * 결합되지 않는다. 동일 참여에 같은 사유·보상은 UK로 한 번만 지급되며, 재호출은 이미 지급된 것을 건너뛴다(멱등).
 */
@Service
@RequiredArgsConstructor
public class RewardService {

  private static final String POINT_REWARD_CODE = "POINT_BASE";

  private final RewardCatalogRepository rewardCatalogRepository;
  private final RewardGrantRepository rewardGrantRepository;
  private final UserBadgeRepository userBadgeRepository;
  private final UserPointService userPointService;

  /** 기본 보상(포인트·배지)을 지급하고 지급 목록과 잔액을 돌려준다. */
  @Transactional
  public BaseRewardResult grantBaseReward(
      UUID userId, UUID participationId, UUID eventId, Integer points, String badgeCode) {
    List<GrantedRewardDto> granted = new ArrayList<>();

    if (points != null && points > 0) {
      RewardCatalog pointCatalog =
          rewardCatalogRepository
              .findByCode(POINT_REWARD_CODE)
              .orElseThrow(
                  () -> new IllegalStateException("POINT_BASE reward catalog is missing."));
      if (!alreadyGranted(participationId, pointCatalog.getId())) {
        RewardGrant grant = saveGrant(userId, pointCatalog, participationId, eventId);
        userPointService.record(userId, points, GrantReason.BASE.name(), grant.getId());
        granted.add(GrantedRewardDto.of(grant, points));
      }
    }

    if (badgeCode != null) {
      rewardCatalogRepository
          .findByCode(badgeCode)
          .ifPresent(
              badge -> {
                if (!alreadyGranted(participationId, badge.getId())) {
                  RewardGrant grant = saveGrant(userId, badge, participationId, eventId);
                  if (!userBadgeRepository.existsByUserIdAndReward_Id(userId, badge.getId())) {
                    userBadgeRepository.save(
                        UserBadge.builder()
                            .userId(userId)
                            .reward(badge)
                            .grantId(grant.getId())
                            .build());
                  }
                  granted.add(GrantedRewardDto.of(grant, badge.getPointAmount()));
                }
              });
    }

    return new BaseRewardResult(granted, userPointService.getBalance(userId));
  }

  private boolean alreadyGranted(UUID participationId, UUID rewardId) {
    return rewardGrantRepository.existsByParticipationIdAndGrantReasonAndReward_Id(
        participationId, GrantReason.BASE, rewardId);
  }

  private RewardGrant saveGrant(
      UUID userId, RewardCatalog reward, UUID participationId, UUID eventId) {
    return rewardGrantRepository.save(
        RewardGrant.builder()
            .userId(userId)
            .reward(reward)
            .participationId(participationId)
            .eventId(eventId)
            .grantReason(GrantReason.BASE)
            .grantedAt(OffsetDateTime.now())
            .build());
  }
}
