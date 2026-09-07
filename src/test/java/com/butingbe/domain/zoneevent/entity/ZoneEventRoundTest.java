package com.butingbe.domain.zoneevent.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ZoneEventRoundTest {

  @Test
  @DisplayName("종료하면 closedAt이 채워진다")
  void closeStampsClosedAt() {
    ZoneEventRound round =
        ZoneEventRound.builder()
            .startsAt(OffsetDateTime.now())
            .endsAt(OffsetDateTime.now().plusDays(1))
            .build();

    round.close();

    assertThat(round.getStatus()).isEqualTo(RoundStatus.CLOSED);
    assertThat(round.getClosedAt()).isNotNull();
  }

  @Test
  @DisplayName("회차 번호가 비어 있을 때만 배정된다")
  void assignsRoundNoOnlyOnce() {
    ZoneEventRound round =
        ZoneEventRound.builder()
            .startsAt(OffsetDateTime.now())
            .endsAt(OffsetDateTime.now().plusDays(1))
            .build();

    round.assignRoundNo(1);
    round.assignRoundNo(2);

    assertThat(round.getRoundNo()).isEqualTo(1);
  }
}
