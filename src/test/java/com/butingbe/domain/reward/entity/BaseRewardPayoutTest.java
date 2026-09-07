package com.butingbe.domain.reward.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaseRewardPayoutTest {

  @Test
  @DisplayName("생성 직후 확정 대기·보류 없음 상태다")
  void startsPendingConfirmAndUnheld() {
    BaseRewardPayout payout = BaseRewardPayout.builder().participationId(UUID.randomUUID()).build();

    assertThat(payout.getStatus()).isEqualTo(BaseRewardPayoutStatus.PENDING_CONFIRM);
    assertThat(payout.getHoldStatus()).isEqualTo(PayoutHoldStatus.NONE);
  }

  @Test
  @DisplayName("보류하면 HELD_REPORT가 된다")
  void holdMarksHeldReport() {
    BaseRewardPayout payout = BaseRewardPayout.builder().participationId(UUID.randomUUID()).build();

    payout.hold();

    assertThat(payout.getHoldStatus()).isEqualTo(PayoutHoldStatus.HELD_REPORT);
  }

  @Test
  @DisplayName("보류 해제하면 NONE으로 돌아간다")
  void releaseHoldMarksNone() {
    BaseRewardPayout payout = BaseRewardPayout.builder().participationId(UUID.randomUUID()).build();
    payout.hold();

    payout.releaseHold();

    assertThat(payout.getHoldStatus()).isEqualTo(PayoutHoldStatus.NONE);
  }
}
