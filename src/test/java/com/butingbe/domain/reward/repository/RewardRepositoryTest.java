package com.butingbe.domain.reward.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.reward.entity.GrantReason;
import com.butingbe.domain.reward.entity.RewardCatalog;
import com.butingbe.domain.reward.entity.RewardGrant;
import com.butingbe.domain.reward.entity.RewardType;
import com.butingbe.domain.reward.entity.UserBadge;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.support.AbstractContainerTest;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RewardRepositoryTest extends AbstractContainerTest {

  @Autowired private RewardCatalogRepository rewardCatalogRepository;
  @Autowired private RewardGrantRepository rewardGrantRepository;
  @Autowired private UserBadgeRepository userBadgeRepository;
  @Autowired private UserRepository userRepository;

  @Test
  @DisplayName("카탈로그를 code로 조회하고 존재 여부를 확인한다")
  void findsCatalogByCode() {
    RewardCatalog badge = savedBadge("SPOT_TEST");

    assertThat(rewardCatalogRepository.findByCode("SPOT_TEST")).contains(badge);
    assertThat(rewardCatalogRepository.existsByCode("SPOT_TEST")).isTrue();
    assertThat(rewardCatalogRepository.existsByCode("NONE")).isFalse();
    assertThat(badge.getRewardType()).isEqualTo(RewardType.BADGE);
    assertThat(badge.getActive()).isTrue();
  }

  @Test
  @DisplayName("참여·사유·보상별 지급 존재 확인과 유저별 지급 이력을 조회한다")
  void findsGrantsByUserAndDedupKey() {
    UUID userId = savedUser();
    RewardCatalog point = savedPoint();
    UUID participationId = UUID.randomUUID();
    RewardGrant grant =
        rewardGrantRepository.save(
            RewardGrant.builder()
                .userId(userId)
                .reward(point)
                .participationId(participationId)
                .eventId(UUID.randomUUID())
                .grantReason(GrantReason.BASE)
                .grantedAt(OffsetDateTime.now())
                .build());

    assertThat(
            rewardGrantRepository.existsByParticipationIdAndGrantReasonAndReward_Id(
                participationId, GrantReason.BASE, point.getId()))
        .isTrue();
    assertThat(rewardGrantRepository.findByUserIdOrderByGrantedAtDesc(userId))
        .containsExactly(grant);

    grant.revoke(OffsetDateTime.now());
    OffsetDateTime firstRevoke = grant.getRevokedAt();
    grant.revoke(OffsetDateTime.now().plusHours(1)); // 멱등: 이미 회수된 것은 그대로
    assertThat(grant.getRevokedAt()).isEqualTo(firstRevoke);
  }

  @Test
  @DisplayName("배지 보유를 유저·보상으로 확인하고 획득순으로 조회한다")
  void findsBadgesByUser() {
    UUID userId = savedUser();
    RewardCatalog badge = savedBadge("SPOT_BADGE");
    RewardGrant grant =
        rewardGrantRepository.save(
            RewardGrant.builder()
                .userId(userId)
                .reward(badge)
                .grantReason(GrantReason.BASE)
                .grantedAt(OffsetDateTime.now())
                .build());
    userBadgeRepository.save(
        UserBadge.builder().userId(userId).reward(badge).grantId(grant.getId()).build());

    assertThat(userBadgeRepository.existsByUserIdAndReward_Id(userId, badge.getId())).isTrue();
    assertThat(userBadgeRepository.findByUserIdOrderByEarnedAtDesc(userId)).hasSize(1);
  }

  private RewardCatalog savedBadge(String code) {
    return rewardCatalogRepository.save(
        RewardCatalog.builder().rewardType(RewardType.BADGE).code(code).name("배지").build());
  }

  private RewardCatalog savedPoint() {
    return rewardCatalogRepository.save(
        RewardCatalog.builder()
            .rewardType(RewardType.POINT)
            .code("POINT_" + UUID.randomUUID())
            .name("포인트")
            .pointAmount(50)
            .build());
  }

  private UUID savedUser() {
    User user =
        userRepository.save(
            User.builder()
                .email("grant-" + UUID.randomUUID() + "@example.com")
                .provider("google")
                .providerId("google-grant-" + UUID.randomUUID())
                .name(new Name("Kim", "Tester"))
                .nickname("granter")
                .role(UserRole.USER)
                .build());
    return user.getId();
  }
}
