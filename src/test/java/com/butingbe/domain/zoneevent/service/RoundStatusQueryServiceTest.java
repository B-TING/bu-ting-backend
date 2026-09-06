package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.zoneevent.dto.response.RoundStatusResDto;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.SlotKind;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.support.AbstractContainerTest;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RoundStatusQueryServiceTest extends AbstractContainerTest {

  @Autowired private RoundStatusQueryService queryService;
  @Autowired private ZoneEventRoundRepository roundRepository;
  @Autowired private ZoneEventRoundSlotRepository slotRepository;

  @Test
  @DisplayName("열린 회차가 있으면 슬롯 구역은 OPEN, 나머지는 REST다")
  void openRoundShowsOpenAndRest() {
    ZoneEventRound round = round(RoundStatus.OPEN, OffsetDateTime.now().minusHours(1));
    slot(round, "SUYEONG_NAMGU", UUID.randomUUID());

    RoundStatusResDto status = queryService.current();

    assertThat(status.status()).isEqualTo(RoundStatus.OPEN);
    assertThat(status.zones()).hasSize(6);
    assertThat(status.zones()).anyMatch(z -> z.slotStatus().equals("OPEN"));
    assertThat(status.zones()).anyMatch(z -> z.slotStatus().equals("REST"));
  }

  @Test
  @DisplayName("열린 회차가 없으면 다음 예정 회차 구역은 UPCOMING이다")
  void upcomingWhenNoOpen() {
    ZoneEventRound round = round(RoundStatus.SCHEDULED, OffsetDateTime.now().plusHours(2));
    slot(round, "YEONGDO", null);

    RoundStatusResDto status = queryService.current();

    assertThat(status.status()).isEqualTo(RoundStatus.SCHEDULED);
    assertThat(status.zones()).anyMatch(z -> z.slotStatus().equals("UPCOMING"));
  }

  @Test
  @DisplayName("열린·예정 회차가 모두 없으면 404다")
  void notFound() {
    assertThatThrownBy(() -> queryService.current()).isInstanceOf(ResourceNotFoundException.class);
  }

  private ZoneEventRound round(RoundStatus status, OffsetDateTime startsAt) {
    return roundRepository.save(
        ZoneEventRound.builder()
            .startsAt(startsAt)
            .endsAt(startsAt.plusDays(1))
            .status(status)
            .build());
  }

  private void slot(ZoneEventRound round, String zoneId, UUID eventId) {
    slotRepository.save(
        ZoneEventRoundSlot.builder()
            .round(round)
            .slotKind(SlotKind.AUTH)
            .zoneId(zoneId)
            .eventId(eventId)
            .build());
  }
}
