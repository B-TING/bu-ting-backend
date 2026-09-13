package com.butingbe.domain.reward.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.reward.dto.response.BaseRewardResult;
import com.butingbe.domain.reward.entity.RewardCatalog;
import com.butingbe.domain.reward.entity.RewardType;
import com.butingbe.domain.reward.repository.RewardCatalogRepository;
import com.butingbe.domain.reward.repository.RewardGrantRepository;
import com.butingbe.domain.reward.repository.UserBadgeRepository;
import com.butingbe.domain.reward.repository.UserPointLedgerRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.support.AbstractContainerTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RewardServiceTest extends AbstractContainerTest {

  @Autowired private RewardService rewardService;
  @Autowired private RewardCatalogRepository rewardCatalogRepository;
  @Autowired private RewardGrantRepository rewardGrantRepository;
  @Autowired private UserBadgeRepository userBadgeRepository;
  @Autowired private UserPointLedgerRepository ledgerRepository;
  @Autowired private UserPointService userPointService;
  @Autowired private UserRepository userRepository;

  private UUID userId;

  @BeforeEach
  void setUp() {
    rewardCatalogRepository.save(
        RewardCatalog.builder()
            .rewardType(RewardType.POINT)
            .code("POINT_BASE")
            .name("기본 포인트")
            .pointAmount(50)
            .build());
    rewardCatalogRepository.save(
        RewardCatalog.builder()
            .rewardType(RewardType.BADGE)
            .code("SPOT_GWANGAN_BRIDGE")
            .name("광안대교 스팟")
            .build());
    userId =
        userRepository
            .save(
                User.builder()
                    .email("reward-" + UUID.randomUUID() + "@example.com")
                    .provider("google")
                    .providerId("google-" + UUID.randomUUID())
                    .name(new Name("Kim", "Tester"))
                    .nickname("rewardee")
                    .role(UserRole.USER)
                    .build())
            .getId();
  }

  @Test
  @DisplayName("포인트와 배지를 지급하고 원장·잔액·배지 보유를 남긴다")
  void grantsPointAndBadge() {
    UUID participationId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();

    BaseRewardResult result =
        rewardService.grantBaseReward(userId, participationId, eventId, 50, "SPOT_GWANGAN_BRIDGE");

    assertThat(result.rewards()).hasSize(2);
    assertThat(result.pointBalance()).isEqualTo(50);
    assertThat(ledgerRepository.sumAmountByUserId(userId)).isEqualTo(50);
    assertThat(userBadgeRepository.findByUserIdOrderByEarnedAtDesc(userId)).hasSize(1);
    assertThat(rewardGrantRepository.findByUserIdOrderByGrantedAtDesc(userId)).hasSize(2);
  }

  @Test
  @DisplayName("같은 참여에 다시 지급하면 중복 없이 건너뛴다(멱등)")
  void grantIsIdempotentPerParticipation() {
    UUID participationId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();

    rewardService.grantBaseReward(userId, participationId, eventId, 50, "SPOT_GWANGAN_BRIDGE");
    BaseRewardResult second =
        rewardService.grantBaseReward(userId, participationId, eventId, 50, "SPOT_GWANGAN_BRIDGE");

    assertThat(second.rewards()).isEmpty();
    assertThat(second.pointBalance()).isEqualTo(50); // 두 번째로 늘지 않음
    assertThat(rewardGrantRepository.findByUserIdOrderByGrantedAtDesc(userId)).hasSize(2);
  }

  @Test
  @DisplayName("배지 코드가 없으면 포인트만 지급한다")
  void grantsPointOnlyWhenNoBadge() {
    BaseRewardResult result =
        rewardService.grantBaseReward(userId, UUID.randomUUID(), UUID.randomUUID(), 50, null);

    assertThat(result.rewards()).hasSize(1);
    assertThat(result.rewards().get(0).rewardType()).isEqualTo("POINT");
    assertThat(userPointService.getBalance(userId)).isEqualTo(50);
  }

  @Test
  @DisplayName("POINT_BASE 카탈로그가 없으면 지급이 중단된다")
  void failsWhenPointCatalogMissing() {
    rewardCatalogRepository.deleteAll();

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                rewardService.grantBaseReward(
                    userId, UUID.randomUUID(), UUID.randomUUID(), 50, null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("포인트가 없으면 배지만 지급한다")
  void grantsBadgeOnlyWhenNoPoints() {
    BaseRewardResult result =
        rewardService.grantBaseReward(
            userId, UUID.randomUUID(), UUID.randomUUID(), null, "SPOT_GWANGAN_BRIDGE");

    assertThat(result.rewards()).hasSize(1);
    assertThat(result.rewards().get(0).rewardType()).isEqualTo("BADGE");
    assertThat(result.pointBalance()).isZero();
  }
}
