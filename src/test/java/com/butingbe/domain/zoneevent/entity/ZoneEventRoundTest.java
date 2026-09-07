package com.butingbe.domain.zoneevent.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.global.error.exception.ConflictException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ZoneEventRoundTest {

  private ZoneEventRound draft() {
    return ZoneEventRound.builder()
        .roundNo(1)
        .name("부산 바다 인증의 날")
        .startsAt(OffsetDateTime.now())
        .endsAt(OffsetDateTime.now().plusDays(1))
        .timezone("Asia/Seoul")
        .roundType(RoundType.REGULAR)
        .build();
  }

  @Test
  void 생성하면_DRAFT다() {
    assertThat(draft().getStatus()).isEqualTo(RoundStatus.DRAFT);
  }

  @Test
  void DRAFT에서_확정하면_SCHEDULED다() {
    ZoneEventRound round = draft();
    round.confirmSchedule();
    assertThat(round.getStatus()).isEqualTo(RoundStatus.SCHEDULED);
  }

  @Test
  void SCHEDULED가_아니면_확정할_수_없다() {
    ZoneEventRound round = draft();
    assertThatThrownBy(round::activate).isInstanceOf(ConflictException.class);
  }

  @Test
  void SCHEDULED에서_activate하면_ACTIVE다() {
    ZoneEventRound round = draft();
    round.confirmSchedule();
    round.activate();
    assertThat(round.getStatus()).isEqualTo(RoundStatus.ACTIVE);
  }

  @Test
  void ACTIVE가_아니면_close할_수_없다() {
    ZoneEventRound round = draft();
    assertThatThrownBy(round::close).isInstanceOf(ConflictException.class);
  }

  @Test
  void DRAFT_SCHEDULED_ACTIVE에서_취소할_수_있고_CLOSED_이후엔_안된다() {
    ZoneEventRound round = draft();
    round.cancel("우천으로 인한 취소");
    assertThat(round.getStatus()).isEqualTo(RoundStatus.CANCELLED);
    assertThat(round.getCancelReason()).isEqualTo("우천으로 인한 취소");

    ZoneEventRound closed = draft();
    closed.confirmSchedule();
    closed.activate();
    closed.close();
    assertThatThrownBy(() -> closed.cancel("사유")).isInstanceOf(ConflictException.class);
  }

  @Test
  void DRAFT_SCHEDULED에서만_메타데이터를_수정할_수_있다() {
    ZoneEventRound round = draft();
    round.applyEditable("새 이름", null, null, null, null);
    assertThat(round.getName()).isEqualTo("새 이름");

    round.confirmSchedule();
    round.activate();
    assertThatThrownBy(() -> round.applyEditable("또 다른 이름", null, null, null, null))
        .isInstanceOf(ConflictException.class);
  }
}
