package com.butingbe.domain.reward.service;

import com.butingbe.domain.reward.entity.RewardGrant;
import com.butingbe.domain.reward.entity.RewardType;
import com.butingbe.domain.reward.entity.UserCoupon;
import com.butingbe.domain.reward.repository.RewardGrantRepository;
import com.butingbe.domain.reward.repository.UserCouponRepository;
import com.butingbe.domain.reward.repository.UserPointLedgerRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참여 회수 시 지급된 보상을 무효화한다(FR-RWD-08).
 *
 * <p>해당 참여의 BASE·TOP_LIKE 지급을 {@code revoked_at}으로 무효 처리하고, 포인트는 원장에 음수 항목으로 되돌린다. 실물 쿠폰은 미사용일 때만
 * 회수한다.
 */
@Service
@RequiredArgsConstructor
public class RewardRevokeService {

  private final RewardGrantRepository rewardGrantRepository;
  private final UserPointLedgerRepository userPointLedgerRepository;
  private final UserCouponRepository userCouponRepository;
  private final UserPointService userPointService;

  /** 참여의 지급 보상을 회수한다. */
  @Transactional
  public void revokeParticipationRewards(UUID participationId) {
    OffsetDateTime now = OffsetDateTime.now();
    for (RewardGrant grant :
        rewardGrantRepository.findByParticipationIdAndRevokedAtIsNull(participationId)) {
      grant.revoke(now);
      if (grant.getReward().getRewardType() == RewardType.POINT) {
        long granted = userPointLedgerRepository.sumAmountByGrantId(grant.getId());
        if (granted != 0) {
          userPointService.record(grant.getUserId(), (int) -granted, "REVOKE", grant.getId());
        }
      } else {
        for (UserCoupon coupon : userCouponRepository.findByGrantId(grant.getId())) {
          coupon.revokeIfUnused();
        }
      }
    }
  }
}
