package com.butingbe.domain.zonetitle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.ParticipationVisibility;
import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.domain.zonetitle.dto.response.EquippedTitleResDto;
import com.butingbe.domain.zonetitle.dto.response.MyZoneTitlesResDto;
import com.butingbe.domain.zonetitle.entity.ZoneTitleDef;
import com.butingbe.domain.zonetitle.repository.UserZoneTitleRepository;
import com.butingbe.domain.zonetitle.repository.ZoneTitleDefRepository;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.support.AbstractContainerTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ZoneTitleServiceTest extends AbstractContainerTest {

  @Autowired private ZoneTitleService zoneTitleService;
  @Autowired private UserZoneTitleRepository userZoneTitleRepository;
  @Autowired private ZoneTitleDefRepository titleDefRepository;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private UserRepository userRepository;

  private UUID userId;
  private AuthenticatedUser user;
  private ZoneEventType type;

  @BeforeEach
  void setUp() {
    seedTitleDefs();
    type =
        zoneEventTypeRepository.save(
            ZoneEventType.builder()
                .typeCode("PLACE_AUTH")
                .name("장소 인증")
                .requiresUpload(true)
                .build());
    User saved = userRepository.save(newUser());
    userId = saved.getId();
    user = AuthenticatedUser.from(saved);
  }

  @Test
  @DisplayName("성공 1회면 T1 발급, 첫 칭호는 자동 장착, 재호출은 멱등")
  void awardT1AndAutoEquip() {
    successInZone("SUYEONG_NAMGU", 1);

    List<EquippedTitleResDto> newly = zoneTitleService.awardTitles(userId, "SUYEONG_NAMGU");

    assertThat(newly).hasSize(1);
    assertThat(newly.get(0).titleCode()).isEqualTo("SUYEONG_NAMGU_T1");
    assertThat(userZoneTitleRepository.countByUserIdAndEquippedIsTrue(userId)).isEqualTo(1);

    // 재호출은 새로 발급하지 않는다(멱등).
    assertThat(zoneTitleService.awardTitles(userId, "SUYEONG_NAMGU")).isEmpty();
  }

  @Test
  @DisplayName("성공 3회면 T1·T2 함께 발급되고 최고 tier가 자동 장착된다")
  void awardMultipleTiers() {
    successInZone("YEONGDO", 3);

    List<EquippedTitleResDto> newly = zoneTitleService.awardTitles(userId, "YEONGDO");

    assertThat(newly)
        .extracting(EquippedTitleResDto::titleCode)
        .containsExactly("YEONGDO_T1", "YEONGDO_T2");
    assertThat(
            userZoneTitleRepository
                .findByUserIdAndEquippedIsTrue(userId)
                .orElseThrow()
                .getTitleDef()
                .getTier())
        .isEqualTo(2);
  }

  @Test
  @DisplayName("대표 칭호를 수동 장착·해제한다")
  void equipAndUnequip() {
    successInZone("CENTRAL_NORTH", 3);
    zoneTitleService.awardTitles(userId, "CENTRAL_NORTH");
    var titles = userZoneTitleRepository.findByUserIdAndRevokedAtIsNull(userId);
    UUID t1Id =
        titles.stream()
            .filter(t -> t.getTitleDef().getTier() == 1)
            .findFirst()
            .orElseThrow()
            .getId();

    EquippedTitleResDto equipped = zoneTitleService.equip(user, t1Id);
    assertThat(equipped.tier()).isEqualTo(1);
    assertThat(userZoneTitleRepository.countByUserIdAndEquippedIsTrue(userId)).isEqualTo(1);

    zoneTitleService.unequip(user);
    assertThat(userZoneTitleRepository.countByUserIdAndEquippedIsTrue(userId)).isZero();
  }

  @Test
  @DisplayName("보유하지 않은 칭호 장착은 403이다")
  void equipNotOwned() {
    assertThatThrownBy(() -> zoneTitleService.equip(user, UUID.randomUUID()))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  @DisplayName("내 칭호 진행도를 구역별로 조회한다")
  void myTitlesProgress() {
    successInZone("SUYEONG_NAMGU", 1);
    zoneTitleService.awardTitles(userId, "SUYEONG_NAMGU");

    MyZoneTitlesResDto result = zoneTitleService.myTitles(user);

    assertThat(result.equipped().titleCode()).isEqualTo("SUYEONG_NAMGU_T1");
    assertThat(result.zones()).hasSize(6);
    var suyeong =
        result.zones().stream()
            .filter(z -> z.zoneId().equals("SUYEONG_NAMGU"))
            .findFirst()
            .orElseThrow();
    assertThat(suyeong.successCount()).isEqualTo(1);
    assertThat(suyeong.currentTier()).isEqualTo(1);
    assertThat(suyeong.nextTier()).isEqualTo(2);
    assertThat(suyeong.remainingToNext()).isEqualTo(2); // T2는 3회
  }

  @Test
  @DisplayName("칭호 정의 18개와 유저별 장착 칭호 배치 조회")
  void allDefsAndBatch() {
    assertThat(zoneTitleService.allDefs()).hasSize(18);

    successInZone("SUYEONG_NAMGU", 1);
    zoneTitleService.awardTitles(userId, "SUYEONG_NAMGU");
    Map<UUID, EquippedTitleResDto> batch =
        zoneTitleService.equippedTitlesByUsers(List.of(userId, UUID.randomUUID()));
    assertThat(batch).containsKey(userId);
    assertThat(zoneTitleService.equippedTitlesByUsers(List.of())).isEmpty();
  }

  @Test
  @DisplayName("미인증 조회는 401이다")
  void unauthenticated() {
    assertThatThrownBy(() -> zoneTitleService.myTitles(null))
        .isInstanceOf(com.butingbe.global.error.exception.UnauthenticatedException.class);
  }

  private void successInZone(String zoneId, int count) {
    ZoneEvent event =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId(zoneId)
                .type(type)
                .title("이벤트")
                .startsAt(java.time.OffsetDateTime.now().minusHours(1))
                .durationMinutes(1440)
                .status(ZoneEventStatus.ACTIVE)
                .baseReward(new RewardSnapshot(50, null, null, null))
                .successLimitPerUser(10)
                .build());
    for (int i = 0; i < count; i++) {
      ZoneEventParticipation p =
          ZoneEventParticipation.builder()
              .event(event)
              .userId(userId)
              .status(ParticipationStatus.SUCCESS)
              .gpsLat(35.1)
              .gpsLng(129.1)
              .joinedAt(java.time.OffsetDateTime.now())
              .visibility(ParticipationVisibility.PUBLIC)
              .build();
      participationRepository.save(p);
    }
  }

  private void seedTitleDefs() {
    int[][] tiers = {{1, 1}, {2, 3}, {3, 7}};
    for (ChatZone zone : ChatZone.values()) {
      for (int[] tr : tiers) {
        titleDefRepository.save(
            ZoneTitleDef.builder()
                .titleCode(zone.name() + "_T" + tr[0])
                .zoneId(zone.name())
                .tier(tr[0])
                .requiredSuccessCount(tr[1])
                .titleName(zone.name() + " T" + tr[0])
                .style("chip")
                .color("#000000")
                .build());
      }
    }
  }

  private User newUser() {
    return User.builder()
        .email("t-" + UUID.randomUUID() + "@example.com")
        .provider("google")
        .providerId("google-" + UUID.randomUUID())
        .name(new Name("Kim", "Tester"))
        .nickname("titler")
        .role(UserRole.USER)
        .build();
  }
}
