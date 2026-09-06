package com.butingbe.domain.reward.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.reward.entity.CouponStatus;
import com.butingbe.domain.reward.entity.GrantReason;
import com.butingbe.domain.reward.entity.RewardCatalog;
import com.butingbe.domain.reward.entity.RewardGrant;
import com.butingbe.domain.reward.entity.RewardType;
import com.butingbe.domain.reward.entity.UserCoupon;
import com.butingbe.domain.reward.repository.RewardCatalogRepository;
import com.butingbe.domain.reward.repository.RewardGrantRepository;
import com.butingbe.domain.reward.repository.UserCouponRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.support.AbstractContainerTest;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RewardRevokeServiceTest extends AbstractContainerTest {

  @Autowired private RewardRevokeService revokeService;
  @Autowired private RewardCatalogRepository rewardCatalogRepository;
  @Autowired private RewardGrantRepository rewardGrantRepository;
  @Autowired private UserCouponRepository userCouponRepository;
  @Autowired private UserPointService userPointService;
  @Autowired private UserRepository userRepository;

  private UUID userId;
  private RewardCatalog point;
  private RewardCatalog coupon;

  @BeforeEach
  void setUp() {
    userId = savedUser().getId();
    point =
        rewardCatalogRepository.save(
            RewardCatalog.builder()
                .rewardType(RewardType.POINT)
                .code("POINT_BASE")
                .name("포인트")
                .pointAmount(50)
                .build());
    coupon =
        rewardCatalogRepository.save(
            RewardCatalog.builder()
                .rewardType(RewardType.COUPON)
                .code("COUPON_X")
                .name("쿠폰")
                .build());
  }

  @Test
  @DisplayName("회수하면 지급이 무효화되고 포인트가 음수로 되돌아간다")
  void revokesPointGrant() {
    UUID participationId = UUID.randomUUID();
    RewardGrant grant = grant(point, participationId);
    userPointService.record(userId, 50, "BASE", grant.getId());
    assertThat(userPointService.getBalance(userId)).isEqualTo(50);

    revokeService.revokeParticipationRewards(participationId);

    assertThat(rewardGrantRepository.findById(grant.getId()).orElseThrow().getRevokedAt())
        .isNotNull();
    assertThat(userPointService.getBalance(userId)).isZero();
  }

  @Test
  @DisplayName("미사용 쿠폰은 회수되고, 이미 사용된 쿠폰은 유지된다")
  void revokesUnusedCouponOnly() {
    UUID unusedPart = UUID.randomUUID();
    RewardGrant unusedGrant = grant(coupon, unusedPart);
    userCouponRepository.save(
        UserCoupon.builder().userId(userId).reward(coupon).grantId(unusedGrant.getId()).build());

    UUID usedPart = UUID.randomUUID();
    RewardGrant usedGrant = grant(coupon, usedPart);
    UserCoupon used =
        userCouponRepository.save(
            UserCoupon.builder().userId(userId).reward(coupon).grantId(usedGrant.getId()).build());
    used.revokeIfUnused(); // 흉내: 먼저 소진 상태로 두기 위해 USED 세팅
    org.springframework.test.util.ReflectionTestUtils.setField(used, "status", CouponStatus.USED);

    revokeService.revokeParticipationRewards(unusedPart);
    revokeService.revokeParticipationRewards(usedPart);

    assertThat(userCouponRepository.findByGrantId(unusedGrant.getId()).get(0).getStatus())
        .isEqualTo(CouponStatus.EXPIRED);
    assertThat(userCouponRepository.findByGrantId(usedGrant.getId()).get(0).getStatus())
        .isEqualTo(CouponStatus.USED);
  }

  private RewardGrant grant(RewardCatalog reward, UUID participationId) {
    return rewardGrantRepository.save(
        RewardGrant.builder()
            .userId(userId)
            .reward(reward)
            .participationId(participationId)
            .eventId(UUID.randomUUID())
            .grantReason(GrantReason.BASE)
            .grantedAt(OffsetDateTime.now())
            .build());
  }

  private User savedUser() {
    return userRepository.save(
        User.builder()
            .email("rv-" + UUID.randomUUID() + "@example.com")
            .provider("google")
            .providerId("google-" + UUID.randomUUID())
            .name(new Name("Kim", "Tester"))
            .nickname("revoked")
            .role(UserRole.USER)
            .build());
  }
}
