package com.butingbe.domain.zoneevent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.SlotKind;
import com.butingbe.domain.zoneevent.entity.ZoneEventBackupTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import com.butingbe.domain.zoneevent.entity.ZoneEventTargetKind;
import com.butingbe.support.AbstractContainerTest;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ZoneEventRoundRepositoryTest extends AbstractContainerTest {

  @Autowired private ZoneEventRoundRepository roundRepository;
  @Autowired private ZoneEventRoundSlotRepository slotRepository;
  @Autowired private ZoneEventBackupTargetRepository backupTargetRepository;

  @Test
  @DisplayName("회차 상태 전이(OPEN/CLOSED/SETTLED)를 저장한다")
  void roundTransitions() {
    ZoneEventRound round =
        roundRepository.save(
            ZoneEventRound.builder()
                .startsAt(OffsetDateTime.now())
                .endsAt(OffsetDateTime.now().plusDays(1))
                .status(RoundStatus.SCHEDULED)
                .build());
    round.open();
    round.close();
    round.settle(OffsetDateTime.now());
    OffsetDateTime settledAt = round.getSettledAt();
    round.settle(OffsetDateTime.now().plusHours(1)); // 멱등

    ZoneEventRound found = roundRepository.findById(round.getId()).orElseThrow();
    assertThat(found.getStatus()).isEqualTo(RoundStatus.SETTLED);
    assertThat(found.getSettledAt()).isEqualTo(settledAt);
    assertThat(found.getTimezone()).isEqualTo("Asia/Seoul");
  }

  @Test
  @DisplayName("상태·시각으로 오픈/종료 대상 회차를 조회한다")
  void findsRoundsByStatusAndTime() {
    roundRepository.save(
        ZoneEventRound.builder()
            .startsAt(OffsetDateTime.now().minusMinutes(1))
            .endsAt(OffsetDateTime.now().plusDays(1))
            .status(RoundStatus.SCHEDULED)
            .build());

    assertThat(
            roundRepository.findByStatusAndStartsAtLessThanEqual(
                RoundStatus.SCHEDULED, OffsetDateTime.now()))
        .hasSize(1);
    assertThat(
            roundRepository.findByStatusAndEndsAtLessThanEqual(
                RoundStatus.OPEN, OffsetDateTime.now()))
        .isEmpty();
  }

  @Test
  @DisplayName("슬롯과 예비 타겟을 회차로 조회한다")
  void findsSlotsAndBackupTargets() {
    ZoneEventRound round =
        roundRepository.save(
            ZoneEventRound.builder()
                .startsAt(OffsetDateTime.now())
                .endsAt(OffsetDateTime.now().plusDays(1))
                .status(RoundStatus.OPEN)
                .build());
    ZoneEventRoundSlot slot =
        slotRepository.save(
            ZoneEventRoundSlot.builder()
                .round(round)
                .slotKind(SlotKind.AUTH)
                .zoneId("SUYEONG_NAMGU")
                .build());
    slot.assignEvent(UUID.randomUUID());
    backupTargetRepository.save(
        ZoneEventBackupTarget.builder()
            .round(round)
            .targetKind(ZoneEventTargetKind.PLACE)
            .placeName("예비 타겟")
            .latitude(35.1)
            .longitude(129.1)
            .radiusM(100)
            .build());

    assertThat(slotRepository.findByRound_Id(round.getId())).hasSize(1);
    assertThat(slotRepository.findByRound_Id(round.getId()).get(0).getEventId()).isNotNull();
    assertThat(backupTargetRepository.findByRound_Id(round.getId())).hasSize(1);
  }
}
