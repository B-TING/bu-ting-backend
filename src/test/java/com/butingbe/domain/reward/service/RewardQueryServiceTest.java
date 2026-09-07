package com.butingbe.domain.reward.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.dto.response.PointLedgerPageResDto;
import com.butingbe.domain.reward.dto.response.UserRewardsResDto;
import com.butingbe.domain.reward.entity.GrantReason;
import com.butingbe.domain.reward.entity.RewardCatalog;
import com.butingbe.domain.reward.entity.RewardGrant;
import com.butingbe.domain.reward.entity.RewardType;
import com.butingbe.domain.reward.entity.UserBadge;
import com.butingbe.domain.reward.repository.RewardCatalogRepository;
import com.butingbe.domain.reward.repository.RewardGrantRepository;
import com.butingbe.domain.reward.repository.UserBadgeRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.global.error.exception.UnauthenticatedException;
import com.butingbe.support.AbstractContainerTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RewardQueryServiceTest extends AbstractContainerTest {

  @Autowired private RewardQueryService rewardQueryService;
  @Autowired private RewardCatalogRepository rewardCatalogRepository;
  @Autowired private RewardGrantRepository rewardGrantRepository;
  @Autowired private UserBadgeRepository userBadgeRepository;
  @Autowired private UserPointService userPointService;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private UserRepository userRepository;

  private UUID userId;
  private AuthenticatedUser user;
  private ZoneEventType type;

  @BeforeEach
  void setUp() {
    type =
        zoneEventTypeRepository.save(
            ZoneEventType.builder()
                .typeCode("PLACE_AUTH")
                .name("장소 인증")
                .requiresUpload(true)
                .build());
    userId =
        userRepository
            .save(
                User.builder()
                    .email("rq-" + UUID.randomUUID() + "@example.com")
                    .provider("google")
                    .providerId("google-" + UUID.randomUUID())
                    .name(new Name("Kim", "Tester"))
                    .nickname("rq")
                    .role(UserRole.USER)
                    .build())
            .getId();
    user = new AuthenticatedUser(userId, "rq@example.com", "rq", List.of());
  }

  @Test
  @DisplayName("배지를 구역별로 묶고 포인트 잔액을 함께 반환한다")
  void myRewardsGroupsBadgesByZone() {
    userPointService.record(userId, 50, "BASE", null);
    ZoneEvent suyeong = savedEvent("SUYEONG_NAMGU");
    ZoneEvent yeongdo = savedEvent("YEONGDO");
    earnBadge("SPOT_A", "광안 배지", suyeong);
    earnBadge("SPOT_B", "영도 배지", yeongdo);

    UserRewardsResDto rewards = rewardQueryService.myRewards(user);

    assertThat(rewards.pointBalance()).isEqualTo(50);
    assertThat(rewards.badges()).hasSize(2);
    assertThat(rewards.badges())
        .anySatisfy(
            group -> {
              assertThat(group.zoneId()).isEqualTo("SUYEONG_NAMGU");
              assertThat(group.items().get(0).code()).isEqualTo("SPOT_A");
            });
    assertThat(rewards.coupons()).isEmpty();
  }

  @Test
  @DisplayName("배지가 없으면 빈 목록과 0 잔액을 반환한다")
  void myRewardsEmpty() {
    UserRewardsResDto rewards = rewardQueryService.myRewards(user);

    assertThat(rewards.pointBalance()).isZero();
    assertThat(rewards.badges()).isEmpty();
  }

  @Test
  @DisplayName("포인트 원장을 createdAt 내림차순 커서 페이징으로 조회한다")
  void pointLedgerCursorPaging() {
    userPointService.record(userId, 10, "BASE", null);
    userPointService.record(userId, 20, "BASE", null);
    userPointService.record(userId, 30, "BASE", null);

    PointLedgerPageResDto first = rewardQueryService.pointLedger(user, null, 2);
    assertThat(first.items()).hasSize(2);
    assertThat(first.hasNext()).isTrue();

    PointLedgerPageResDto second = rewardQueryService.pointLedger(user, first.nextCursor(), 2);
    assertThat(second.items()).hasSize(1);
    assertThat(second.hasNext()).isFalse();
  }

  @Test
  @DisplayName("size가 없으면 기본 크기로 원장을 조회한다")
  void pointLedgerDefaultSize() {
    userPointService.record(userId, 10, "BASE", null);

    PointLedgerPageResDto page = rewardQueryService.pointLedger(user, null, null);

    assertThat(page.items()).hasSize(1);
    assertThat(page.hasNext()).isFalse();
  }

  @Test
  @DisplayName("잘못된 원장 커서는 400이다")
  void invalidLedgerCursor() {
    assertThatThrownBy(() -> rewardQueryService.pointLedger(user, "!!bad!!", 20))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("형식이 맞지 않는(구분자 없는) 원장 커서도 400이다")
  void malformedLedgerCursor() {
    String noPipe =
        java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("nopipe".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertThatThrownBy(() -> rewardQueryService.pointLedger(user, noPipe, 20))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("미인증 조회는 401이다")
  void unauthenticated() {
    assertThatThrownBy(() -> rewardQueryService.myRewards(null))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> rewardQueryService.pointLedger(null, null, 20))
        .isInstanceOf(UnauthenticatedException.class);
  }

  private ZoneEvent savedEvent(String zoneId) {
    return zoneEventRepository.save(
        ZoneEvent.builder()
            .zoneId(zoneId)
            .type(type)
            .title("이벤트 " + zoneId)
            .startsAt(OffsetDateTime.now().minusHours(1))
            .durationMinutes(1440)
            .status(ZoneEventStatus.ACTIVE)
            .baseReward(new RewardSnapshot(50, "SPOT", null, null))
            .successLimitPerUser(1)
            .build());
  }

  private void earnBadge(String code, String name, ZoneEvent event) {
    RewardCatalog badge =
        rewardCatalogRepository.save(
            RewardCatalog.builder().rewardType(RewardType.BADGE).code(code).name(name).build());
    RewardGrant grant =
        rewardGrantRepository.save(
            RewardGrant.builder()
                .userId(userId)
                .reward(badge)
                .participationId(UUID.randomUUID())
                .eventId(event.getId())
                .grantReason(GrantReason.BASE)
                .grantedAt(OffsetDateTime.now())
                .build());
    userBadgeRepository.save(
        UserBadge.builder().userId(userId).reward(badge).grantId(grant.getId()).build());
  }
}
