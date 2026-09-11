package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.reward.entity.BaseRewardPayout;
import com.butingbe.domain.reward.repository.BaseRewardPayoutRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.domain.zoneevent.entity.ParticipationStatus;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.SlotKind;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.support.AbstractContainerTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminZoneEventStatsServiceTest extends AbstractContainerTest {

  @Autowired private AdminZoneEventStatsService service;
  @Autowired private ZoneEventRoundRepository roundRepository;
  @Autowired private ZoneEventRoundSlotRepository slotRepository;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private BaseRewardPayoutRepository baseRewardPayoutRepository;
  @Autowired private UserRepository userRepository;

  private AuthenticatedUser operator;

  @BeforeEach
  void setUp() {
    operator =
        new AuthenticatedUser(
            userRepository
                .save(
                    User.builder()
                        .email("op-" + UUID.randomUUID() + "@example.com")
                        .provider("google")
                        .providerId("google-" + UUID.randomUUID())
                        .name(new Name("Kim", "Tester"))
                        .nickname("op")
                        .role(UserRole.USER)
                        .build())
                .getId(),
            "op@example.com",
            "op",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  @Test
  @DisplayName("roundId도 from/to도 없으면 400")
  void requiresRoundOrRange() {
    assertThatThrownBy(() -> service.stats(operator, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("error.zone_event.stats.round_or_range_required");
  }

  @Test
  @DisplayName("존재하지 않는 roundId면 404")
  void unknownRoundIdIs404() {
    assertThatThrownBy(() -> service.stats(operator, UUID.randomUUID(), null, null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("error.zone_event.not_found");
  }

  @Test
  @DisplayName("이벤트가 배정된 슬롯은 참여·성공·기본지급 건수를 집계한다")
  void aggregatesSlotStats() {
    OffsetDateTime now = OffsetDateTime.now();
    ZoneEventRound round =
        roundRepository.save(
            ZoneEventRound.builder()
                .roundNo(1)
                .startsAt(now.minusHours(2))
                .endsAt(now.plusHours(22))
                .status(RoundStatus.ACTIVE)
                .build());
    ZoneEventType type =
        zoneEventTypeRepository.save(
            ZoneEventType.builder()
                .typeCode("PLACE_AUTH")
                .name("장소 인증")
                .requiresUpload(true)
                .build());
    ZoneEvent event =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId("SUYEONG_NAMGU")
                .type(type)
                .roundId(round.getId())
                .title("이벤트")
                .startsAt(now.minusHours(1))
                .durationMinutes(1440)
                .status(ZoneEventStatus.ACTIVE)
                .successLimitPerUser(1)
                .build());
    ZoneEventRoundSlot slot =
        slotRepository.save(
            ZoneEventRoundSlot.builder()
                .round(round)
                .slotKind(SlotKind.AUTH)
                .zoneId("SUYEONG_NAMGU")
                .eventId(event.getId())
                .build());

    ZoneEventParticipation success =
        participationRepository.save(
            ZoneEventParticipation.builder()
                .event(event)
                .userId(UUID.randomUUID())
                .status(ParticipationStatus.SUCCESS)
                .gpsLat(35.1)
                .gpsLng(129.1)
                .joinedAt(now)
                .build());
    baseRewardPayoutRepository.save(
        BaseRewardPayout.builder()
            .participationId(success.getId())
            .reward(new com.butingbe.domain.zoneevent.entity.RewardSnapshot(50, null, null, null))
            .build());
    var paid = baseRewardPayoutRepository.findByParticipationId(success.getId()).orElseThrow();
    paid.confirm(operator.id());
    paid.markSent(now, null);
    baseRewardPayoutRepository.saveAndFlush(paid);

    var result = service.stats(operator, round.getId(), null, null);

    assertThat(result.slots()).hasSize(1);
    var item = result.slots().get(0);
    assertThat(item.eventId()).isEqualTo(event.getId().toString());
    assertThat(item.joinedCount()).isEqualTo(1);
    assertThat(item.successCount()).isEqualTo(1);
    assertThat(item.basePaidCount()).isEqualTo(1);
    // successRate 분모는 submittedCount(제출 기준)다. 이 참여는 SUCCESS지만 제출 이력을 연결하지 않아
    // submittedCount=0이므로, successCount가 1이어도 successRate는 NaN이 아니라 0.0이어야 한다.
    assertThat(item.submittedCount()).isZero();
    assertThat(item.successRate()).isEqualTo(0.0);
    assertThat(item.topContent()).isNotNull();
  }

  @Test
  @DisplayName("공개 성공 참여가 없으면 topContent는 null이다")
  void topContentIsNullWhenNoPublicSuccessParticipation() {
    OffsetDateTime now = OffsetDateTime.now();
    ZoneEventRound round =
        roundRepository.save(
            ZoneEventRound.builder()
                .roundNo(3)
                .startsAt(now.minusHours(2))
                .endsAt(now.plusHours(22))
                .status(RoundStatus.ACTIVE)
                .build());
    ZoneEventType type =
        zoneEventTypeRepository.save(
            ZoneEventType.builder()
                .typeCode("PLACE_AUTH")
                .name("장소 인증")
                .requiresUpload(true)
                .build());
    ZoneEvent event =
        zoneEventRepository.save(
            ZoneEvent.builder()
                .zoneId("SUYEONG_NAMGU")
                .type(type)
                .roundId(round.getId())
                .title("이벤트")
                .startsAt(now.minusHours(1))
                .durationMinutes(1440)
                .status(ZoneEventStatus.ACTIVE)
                .successLimitPerUser(1)
                .build());
    slotRepository.save(
        ZoneEventRoundSlot.builder()
            .round(round)
            .slotKind(SlotKind.AUTH)
            .zoneId("SUYEONG_NAMGU")
            .eventId(event.getId())
            .build());

    var result = service.stats(operator, round.getId(), null, null);

    assertThat(result.slots()).hasSize(1);
    assertThat(result.slots().get(0).topContent()).isNull();
  }

  @Test
  @DisplayName("이벤트가 배정되지 않은 슬롯은 0값 항목을 돌려준다")
  void returnsEmptyItemForUnassignedSlot() {
    OffsetDateTime now = OffsetDateTime.now();
    ZoneEventRound round =
        roundRepository.save(
            ZoneEventRound.builder()
                .roundNo(2)
                .startsAt(now.minusHours(2))
                .endsAt(now.plusHours(22))
                .status(RoundStatus.DRAFT)
                .build());
    slotRepository.save(
        ZoneEventRoundSlot.builder()
            .round(round)
            .slotKind(SlotKind.AUTH)
            .zoneId("HAEUNDAE_GIJANG")
            .build());

    var result = service.stats(operator, round.getId(), null, null);

    assertThat(result.slots()).hasSize(1);
    assertThat(result.slots().get(0).eventId()).isNull();
    assertThat(result.slots().get(0).joinedCount()).isZero();
  }
}
