package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.entity.GrantReason;
import com.butingbe.domain.reward.entity.RewardCatalog;
import com.butingbe.domain.reward.entity.RewardGrant;
import com.butingbe.domain.reward.entity.RewardType;
import com.butingbe.domain.reward.repository.RewardCatalogRepository;
import com.butingbe.domain.reward.repository.RewardGrantRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.dto.response.ParticipationHistoryPageResDto;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
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
class ZoneEventParticipationQueryServiceTest extends AbstractContainerTest {

  @Autowired private ZoneEventParticipationQueryService queryService;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private RewardCatalogRepository rewardCatalogRepository;
  @Autowired private RewardGrantRepository rewardGrantRepository;
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
                    .email("history-" + UUID.randomUUID() + "@example.com")
                    .provider("google")
                    .providerId("google-" + UUID.randomUUID())
                    .name(new Name("Kim", "Tester"))
                    .nickname("historian")
                    .role(UserRole.USER)
                    .build())
            .getId();
    user = new AuthenticatedUser(userId, "h@example.com", "h", List.of());
  }

  @Test
  @DisplayName("내 이력을 joinedAt 내림차순 커서 페이징으로 조회한다")
  void historyCursorPaging() {
    ZoneEvent event = savedEvent("SUYEONG_NAMGU");
    for (int i = 0; i < 3; i++) {
      savedParticipation(
          event, ParticipationStatus.CANCELLED, OffsetDateTime.now().minusMinutes(i));
    }

    ParticipationHistoryPageResDto first =
        queryService.history(user, null, null, List.of(), null, null, null, 2);
    assertThat(first.items()).hasSize(2);
    assertThat(first.hasNext()).isTrue();
    assertThat(first.nextCursor()).isNotNull();

    ParticipationHistoryPageResDto second =
        queryService.history(user, null, null, List.of(), null, null, first.nextCursor(), 2);
    assertThat(second.items()).hasSize(1);
    assertThat(second.hasNext()).isFalse();
    // 첫 페이지가 더 최근(joinedAt 큰) 것
    assertThat(first.items().get(0).joinedAt()).isAfterOrEqualTo(first.items().get(1).joinedAt());
  }

  @Test
  @DisplayName("구역·상태 필터로 이력을 좁힌다")
  void historyFilters() {
    ZoneEvent suyeong = savedEvent("SUYEONG_NAMGU");
    ZoneEvent yeongdo = savedEvent("YEONGDO");
    savedParticipation(suyeong, ParticipationStatus.SUCCESS, OffsetDateTime.now());
    savedParticipation(yeongdo, ParticipationStatus.CANCELLED, OffsetDateTime.now());

    ParticipationHistoryPageResDto byZone =
        queryService.history(user, "SUYEONG_NAMGU", null, List.of(), null, null, null, 20);
    assertThat(byZone.items()).hasSize(1);
    assertThat(byZone.items().get(0).event().zone().zoneId()).isEqualTo("SUYEONG_NAMGU");

    ParticipationHistoryPageResDto byStatus =
        queryService.history(
            user, null, null, List.of(ParticipationStatus.SUCCESS), null, null, null, 20);
    assertThat(byStatus.items()).hasSize(1);
    assertThat(byStatus.items().get(0).status()).isEqualTo("SUCCESS");
  }

  @Test
  @DisplayName("성공 참여의 지급 보상을 이력에 함께 담는다")
  void historyIncludesRewards() {
    ZoneEvent event = savedEvent("SUYEONG_NAMGU");
    ZoneEventParticipation participation =
        savedParticipation(event, ParticipationStatus.SUCCESS, OffsetDateTime.now());
    RewardCatalog point =
        rewardCatalogRepository.save(
            RewardCatalog.builder()
                .rewardType(RewardType.POINT)
                .code("POINT_BASE")
                .name("기본 포인트")
                .pointAmount(50)
                .build());
    rewardGrantRepository.save(
        RewardGrant.builder()
            .userId(userId)
            .reward(point)
            .participationId(participation.getId())
            .eventId(event.getId())
            .grantReason(GrantReason.BASE)
            .grantedAt(OffsetDateTime.now())
            .build());

    ParticipationHistoryPageResDto page =
        queryService.history(user, null, null, List.of(), null, null, null, 20);

    assertThat(page.items().get(0).rewards()).hasSize(1);
    assertThat(page.items().get(0).rewards().get(0).code()).isEqualTo("POINT_BASE");
  }

  @Test
  @DisplayName("이벤트별 내 참여를 최신순으로 조회한다(취소 포함)")
  void myEventParticipations() {
    ZoneEvent event = savedEvent("SUYEONG_NAMGU");
    savedParticipation(event, ParticipationStatus.CANCELLED, OffsetDateTime.now().minusMinutes(5));
    savedParticipation(event, ParticipationStatus.SUCCESS, OffsetDateTime.now());

    var result = queryService.myEventParticipations(user, event.getId());

    assertThat(result).hasSize(2);
    assertThat(result.get(0).status()).isEqualTo("SUCCESS"); // 최신순
  }

  @Test
  @DisplayName("타입·기간 필터와 기본 페이지 크기로 이력을 좁힌다")
  void historyTypeAndPeriodFiltersWithDefaultSize() {
    ZoneEvent event = savedEvent("SUYEONG_NAMGU");
    savedParticipation(event, ParticipationStatus.SUCCESS, OffsetDateTime.now());

    ParticipationHistoryPageResDto page =
        queryService.history(
            user,
            null,
            "PLACE_AUTH",
            List.of(),
            OffsetDateTime.now().minusDays(1),
            OffsetDateTime.now().plusDays(1),
            null,
            null); // size null → 기본값
    assertThat(page.items()).hasSize(1);

    // 기간 밖이면 비어 있다(빈 페이지 경로)
    ParticipationHistoryPageResDto empty =
        queryService.history(
            user,
            null,
            "PLACE_AUTH",
            List.of(),
            OffsetDateTime.now().plusDays(1),
            OffsetDateTime.now().plusDays(2),
            null,
            null);
    assertThat(empty.items()).isEmpty();
    assertThat(empty.hasNext()).isFalse();
  }

  @Test
  @DisplayName("잘못된 커서는 400이다")
  void invalidCursor() {
    assertThatThrownBy(
            () -> queryService.history(user, null, null, List.of(), null, null, "!!bad!!", 20))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("형식이 맞지 않는(구분자 없는) 커서도 400이다")
  void malformedCursorWithoutDelimiter() {
    String noPipe =
        java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("nopipe".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertThatThrownBy(
            () -> queryService.history(user, null, null, List.of(), null, null, noPipe, 20))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("미인증 이력 조회는 401이다")
  void unauthenticated() {
    assertThatThrownBy(
            () -> queryService.history(null, null, null, List.of(), null, null, null, 20))
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
            .baseReward(new RewardSnapshot(50, "SPOT_GWANGAN_BRIDGE", null, null))
            .successLimitPerUser(1)
            .build());
  }

  private ZoneEventParticipation savedParticipation(
      ZoneEvent event, ParticipationStatus status, OffsetDateTime joinedAt) {
    ZoneEventParticipation p =
        ZoneEventParticipation.builder()
            .event(event)
            .userId(userId)
            .status(status)
            .gpsLat(35.15)
            .gpsLng(129.11)
            .joinedAt(joinedAt)
            .build();
    return participationRepository.save(p);
  }
}
