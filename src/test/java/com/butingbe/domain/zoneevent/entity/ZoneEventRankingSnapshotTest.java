package com.butingbe.domain.zoneevent.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ZoneEventRankingSnapshotTest {

  @Test
  @DisplayName("생성 직후에는 확정되지 않은 상태다")
  void startsNotFinalized() {
    ZoneEventRankingSnapshot snapshot =
        ZoneEventRankingSnapshot.builder()
            .eventId(UUID.randomUUID())
            .closedAt(OffsetDateTime.now())
            .participationId(UUID.randomUUID())
            .rankN(5)
            .likeCountAtClose(24L)
            .tied(true)
            .build();

    assertThat(snapshot.getFinalized()).isFalse();
    assertThat(snapshot.getTied()).isTrue();
    assertThat(snapshot.getVersion()).isEqualTo(1);
  }

  @Test
  @DisplayName("확정하면 finalized가 true가 된다")
  void markFinalizedSetsFinalized() {
    ZoneEventRankingSnapshot snapshot =
        ZoneEventRankingSnapshot.builder()
            .eventId(UUID.randomUUID())
            .closedAt(OffsetDateTime.now())
            .participationId(UUID.randomUUID())
            .rankN(1)
            .likeCountAtClose(50L)
            .build();

    snapshot.markFinalized();

    assertThat(snapshot.getFinalized()).isTrue();
  }
}
