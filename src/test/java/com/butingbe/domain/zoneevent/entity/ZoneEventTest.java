package com.butingbe.domain.zoneevent.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.global.error.exception.ConflictException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ZoneEventTest {

  @Test
  void SCHEDULED가_아니면_activate할_수_없다() {
    ZoneEvent event = buildEvent(ZoneEventStatus.ACTIVE);
    assertThatThrownBy(event::activate).isInstanceOf(ConflictException.class);
  }

  @Test
  void ACTIVE가_아니면_close할_수_없다() {
    ZoneEvent event = buildEvent(ZoneEventStatus.SCHEDULED);
    assertThatThrownBy(event::close).isInstanceOf(ConflictException.class);
  }

  private ZoneEvent buildEvent(ZoneEventStatus status) {
    return ZoneEvent.builder()
        .zoneId("YEONGDO")
        .startsAt(OffsetDateTime.now())
        .durationMinutes(60)
        .status(status)
        .baseReward(new RewardSnapshot(10, null, null, null))
        .build();
  }
}
