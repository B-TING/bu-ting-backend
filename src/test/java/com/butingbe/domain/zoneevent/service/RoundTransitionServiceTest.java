package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.SlotKind;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventParticipationRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRankingSnapshotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.support.AbstractContainerTest;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RoundTransitionServiceTest extends AbstractContainerTest {

  @Autowired private RoundTransitionService transitionService;
  @Autowired private ZoneEventRoundRepository roundRepository;
  @Autowired private ZoneEventRoundSlotRepository slotRepository;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;
  @Autowired private ZoneEventParticipationRepository participationRepository;
  @Autowired private ZoneEventRankingSnapshotRepository snapshotRepository;

  private ZoneEventType type;
  private static int roundNoSeq = 1000;

  @BeforeEach
  void setUp() {
    type =
        zoneEventTypeRepository.save(
            ZoneEventType.builder()
                .typeCode("PLACE_AUTH")
                .name("장소 인증")
                .requiresUpload(true)
                .build());
  }

  @Test
  @DisplayName("시작 시각이 지난 SCHEDULED 회차는 ACTIVE로, 연결 이벤트도 ACTIVE로 바뀐다")
  void syncActivates() {
    ZoneEventRound round = savedRound(RoundStatus.SCHEDULED, -1, 1);
    ZoneEvent event = savedEvent(ZoneEventStatus.SCHEDULED);
    savedSlot(round, "SUYEONG_NAMGU", event.getId());

    transitionService.sync(round, OffsetDateTime.now());

    assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus())
        .isEqualTo(RoundStatus.ACTIVE);
    assertThat(zoneEventRepository.findById(event.getId()).orElseThrow().getStatus())
        .isEqualTo(ZoneEventStatus.ACTIVE);
  }

  @Test
  @DisplayName("종료 시각이 지난 ACTIVE 회차는 CLOSED로, 연결 이벤트도 CLOSED로 바뀐다")
  void syncCloses() {
    ZoneEventRound round = savedRound(RoundStatus.ACTIVE, -2, -1);
    ZoneEvent event = savedEvent(ZoneEventStatus.ACTIVE);
    savedSlot(round, "YEONGDO", event.getId());

    transitionService.sync(round, OffsetDateTime.now());

    assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus())
        .isEqualTo(RoundStatus.CLOSED);
    assertThat(zoneEventRepository.findById(event.getId()).orElseThrow().getStatus())
        .isEqualTo(ZoneEventStatus.CLOSED);
  }

  @Test
  @DisplayName("시작·종료가 모두 지난 SCHEDULED 회차는 sync 한 번으로 CLOSED까지 이어서 전환된다")
  void syncCascadesActivateAndCloseInOneCall() {
    ZoneEventRound round = savedRound(RoundStatus.SCHEDULED, -2, -1);
    ZoneEvent event = savedEvent(ZoneEventStatus.SCHEDULED);
    savedSlot(round, "OLD_DOWNTOWN", event.getId());

    transitionService.sync(round, OffsetDateTime.now());

    assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus())
        .isEqualTo(RoundStatus.CLOSED);
    assertThat(zoneEventRepository.findById(event.getId()).orElseThrow().getStatus())
        .isEqualTo(ZoneEventStatus.CLOSED);
  }

  @Test
  @DisplayName("아직 시작 전이면 아무것도 바뀌지 않는다(멱등)")
  void syncNoopWhenNotDue() {
    ZoneEventRound round = savedRound(RoundStatus.SCHEDULED, 1, 2);

    transitionService.sync(round, OffsetDateTime.now());

    assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus())
        .isEqualTo(RoundStatus.SCHEDULED);
  }

  @Test
  @DisplayName("syncAll은 대상 회차 전체를 훑어 전환한다")
  void syncAllScansEverything() {
    savedRound(RoundStatus.SCHEDULED, -1, 1);
    savedRound(RoundStatus.ACTIVE, -2, -1);

    transitionService.syncAll(OffsetDateTime.now());

    assertThat(roundRepository.findAll())
        .extracting(ZoneEventRound::getStatus)
        .containsExactlyInAnyOrder(RoundStatus.ACTIVE, RoundStatus.CLOSED);
  }

  @Test
  @DisplayName("회차 종료 전환 시 이벤트별 좋아요 스냅샷이 얼려진다")
  void syncFreezesRankingSnapshotOnClose() {
    ZoneEventRound round = savedRound(RoundStatus.ACTIVE, -2, -1);
    ZoneEvent event = savedEventWithExcellence(ZoneEventStatus.ACTIVE, 1);
    savedSlot(round, "YEONGDO", event.getId());
    success(event, 5);

    transitionService.sync(round, OffsetDateTime.now());

    assertThat(snapshotRepository.countByEventId(event.getId())).isEqualTo(1);
  }

  private ZoneEventRound savedRound(RoundStatus status, int startsDaysOffset, int endsDaysOffset) {
    return roundRepository.save(
        ZoneEventRound.builder()
            .roundNo(roundNoSeq++)
            .startsAt(OffsetDateTime.now().plusDays(startsDaysOffset))
            .endsAt(OffsetDateTime.now().plusDays(endsDaysOffset))
            .status(status)
            .build());
  }

  private ZoneEvent savedEvent(ZoneEventStatus status) {
    return zoneEventRepository.save(
        ZoneEvent.builder()
            .zoneId("SUYEONG_NAMGU")
            .type(type)
            .title("이벤트")
            .startsAt(OffsetDateTime.now())
            .durationMinutes(1440)
            .status(status)
            .baseReward(new RewardSnapshot(50, null, null, null))
            .successLimitPerUser(1)
            .build());
  }

  private ZoneEvent savedEventWithExcellence(ZoneEventStatus status, int topN) {
    return zoneEventRepository.save(
        ZoneEvent.builder()
            .zoneId("SUYEONG_NAMGU")
            .type(type)
            .title("이벤트")
            .startsAt(OffsetDateTime.now())
            .durationMinutes(1440)
            .status(status)
            .baseReward(new RewardSnapshot(50, null, null, null))
            .excellenceReward(new RewardSnapshot(null, null, topN, "COUPON_TOP"))
            .successLimitPerUser(10)
            .build());
  }

  private ZoneEventParticipation success(ZoneEvent event, long likeCount) {
    ZoneEventParticipation p =
        ZoneEventParticipation.builder()
            .event(event)
            .userId(UUID.randomUUID())
            .status(com.butingbe.domain.zoneevent.entity.ParticipationStatus.SUCCESS)
            .gpsLat(35.1)
            .gpsLng(129.1)
            .joinedAt(OffsetDateTime.now())
            .visibility(com.butingbe.domain.zoneevent.entity.ParticipationVisibility.PUBLIC)
            .build();
    p = participationRepository.save(p);
    ReflectionTestUtils.setField(p, "likeCount", likeCount);
    ReflectionTestUtils.setField(p, "completedAt", OffsetDateTime.now());
    return participationRepository.save(p);
  }

  private ZoneEventRoundSlot savedSlot(
      ZoneEventRound round, String zoneId, java.util.UUID eventId) {
    return slotRepository.save(
        ZoneEventRoundSlot.builder()
            .round(round)
            .slotKind(SlotKind.AUTH)
            .zoneId(zoneId)
            .eventId(eventId)
            .build());
  }
}
