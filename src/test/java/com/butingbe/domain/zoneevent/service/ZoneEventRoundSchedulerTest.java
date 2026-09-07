package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.SlotKind;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventType;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventTypeRepository;
import com.butingbe.support.AbstractContainerTest;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ZoneEventRoundSchedulerTest extends AbstractContainerTest {

  @Autowired private ZoneEventRoundScheduler scheduler;
  @Autowired private ZoneEventRoundRepository roundRepository;
  @Autowired private ZoneEventRoundSlotRepository slotRepository;
  @Autowired private ZoneEventRepository zoneEventRepository;
  @Autowired private ZoneEventTypeRepository zoneEventTypeRepository;

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
  }

  @Test
  @DisplayName("시작 시각이 지난 SCHEDULED 회차를 OPEN하고 슬롯 이벤트를 ACTIVE로 바꾼다")
  void opensDueRoundsAndActivatesEvents() {
    ZoneEventRound round = savedRound(RoundStatus.SCHEDULED, -1, 1);
    ZoneEvent event = savedEvent(ZoneEventStatus.SCHEDULED);
    savedSlot(round, "SUYEONG_NAMGU", event.getId());

    scheduler.advance(OffsetDateTime.now());

    assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus())
        .isEqualTo(RoundStatus.OPEN);
    assertThat(zoneEventRepository.findById(event.getId()).orElseThrow().getStatus())
        .isEqualTo(ZoneEventStatus.ACTIVE);
  }

  @Test
  @DisplayName("종료 시각이 지난 OPEN 회차를 CLOSED하고 슬롯 이벤트를 CLOSED로 바꾼다")
  void closesEndedRoundsAndEvents() {
    ZoneEventRound round = savedRound(RoundStatus.OPEN, -2, -1);
    ZoneEvent event = savedEvent(ZoneEventStatus.ACTIVE);
    savedSlot(round, "YEONGDO", event.getId());

    scheduler.advance(OffsetDateTime.now());

    assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus())
        .isEqualTo(RoundStatus.CLOSED);
    assertThat(zoneEventRepository.findById(event.getId()).orElseThrow().getStatus())
        .isEqualTo(ZoneEventStatus.CLOSED);
  }

  @Test
  @DisplayName("두 번 실행해도 결과가 같다(멱등)")
  void idempotentOnRerun() {
    ZoneEventRound round = savedRound(RoundStatus.SCHEDULED, -1, 1);
    ZoneEvent event = savedEvent(ZoneEventStatus.SCHEDULED);
    savedSlot(round, "CENTRAL_NORTH", event.getId());

    scheduler.advance(OffsetDateTime.now());
    scheduler.advance(OffsetDateTime.now()); // 재실행

    assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus())
        .isEqualTo(RoundStatus.OPEN);
    assertThat(zoneEventRepository.findById(event.getId()).orElseThrow().getStatus())
        .isEqualTo(ZoneEventStatus.ACTIVE);
  }

  @Test
  @DisplayName("아직 시작 전인 회차는 건드리지 않는다")
  void leavesFutureRounds() {
    ZoneEventRound future = savedRound(RoundStatus.SCHEDULED, 1, 2);

    scheduler.advance(OffsetDateTime.now());

    assertThat(roundRepository.findById(future.getId()).orElseThrow().getStatus())
        .isEqualTo(RoundStatus.SCHEDULED);
  }

  @Test
  @DisplayName("이벤트가 배정되지 않은 슬롯은 건너뛰고 회차만 연다")
  void opensRoundWithSlotButNoEvent() {
    ZoneEventRound round = savedRound(RoundStatus.SCHEDULED, -1, 1);
    savedSlot(round, "WESTERN_BUSAN", null);

    scheduler.advance(OffsetDateTime.now());

    assertThat(roundRepository.findById(round.getId()).orElseThrow().getStatus())
        .isEqualTo(RoundStatus.OPEN);
  }

  @Test
  @DisplayName("이미 ACTIVE인 슬롯 이벤트는 다시 활성화하지 않는다")
  void skipsEventAlreadyInTargetState() {
    ZoneEventRound round = savedRound(RoundStatus.SCHEDULED, -1, 1);
    ZoneEvent alreadyActive = savedEvent(ZoneEventStatus.ACTIVE);
    savedSlot(round, "OLD_DOWNTOWN", alreadyActive.getId());

    scheduler.advance(OffsetDateTime.now());

    assertThat(zoneEventRepository.findById(alreadyActive.getId()).orElseThrow().getStatus())
        .isEqualTo(ZoneEventStatus.ACTIVE);
  }

  @Test
  @DisplayName("스케줄 진입 메서드는 예외 없이 실행된다")
  void scheduledEntryRuns() {
    scheduler.advanceRounds();
  }

  private ZoneEventRound savedRound(RoundStatus status, int startsDaysOffset, int endsDaysOffset) {
    return roundRepository.save(
        ZoneEventRound.builder()
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
