package com.butingbe.domain.reward.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RewardPayoutTest {

  @Test
  @DisplayName("생성 직후 배정 대기·보류 없음 상태다")
  void startsPendingAssignAndUnheld() {
    RewardPayout payout =
        RewardPayout.builder()
            .eventId(UUID.randomUUID())
            .participationId(UUID.randomUUID())
            .rankN(1)
            .likeCountAtClose(42L)
            .build();

    assertThat(payout.getStatus()).isEqualTo(RewardPayoutStatus.PENDING_ASSIGN);
    assertThat(payout.getHoldStatus()).isEqualTo(PayoutHoldStatus.NONE);
  }

  @Test
  @DisplayName("보류 후 해제하면 지급 단계는 그대로다")
  void holdThenReleaseKeepsStatus() {
    RewardPayout payout =
        RewardPayout.builder()
            .eventId(UUID.randomUUID())
            .participationId(UUID.randomUUID())
            .rankN(1)
            .likeCountAtClose(42L)
            .build();

    payout.hold();
    assertThat(payout.getHoldStatus()).isEqualTo(PayoutHoldStatus.HELD_REPORT);

    payout.releaseHold();
    assertThat(payout.getHoldStatus()).isEqualTo(PayoutHoldStatus.NONE);
    assertThat(payout.getStatus()).isEqualTo(RewardPayoutStatus.PENDING_ASSIGN);
  }
}
