package com.butingbe.domain.zoneevent.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.global.error.exception.ConflictException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

  @Test
  @DisplayName("FAIL 참여이고 이벤트가 ACTIVE이며 마감 전이면 재제출할 수 있다")
  void acceptsResubmissionWhenFailedAndActiveBeforeDeadline() {
    ZoneEvent event = buildEvent(ZoneEventStatus.ACTIVE);

    assertThat(event.acceptsResubmission(ParticipationStatus.FAIL, OffsetDateTime.now())).isTrue();
  }

  @Test
  @DisplayName("이벤트가 마감 전이라도 ACTIVE가 아니면(취소·종료) 재제출할 수 없다")
  void rejectsResubmissionWhenEventNotActive() {
    // submit()은 ACTIVE를 요구하므로, 자연 마감 전에 종료·취소된 이벤트는 재제출 가능으로 보이면 안 된다.
    assertThat(
            buildEvent(ZoneEventStatus.CLOSED)
                .acceptsResubmission(ParticipationStatus.FAIL, OffsetDateTime.now()))
        .isFalse();
    assertThat(
            buildEvent(ZoneEventStatus.CANCELLED)
                .acceptsResubmission(ParticipationStatus.FAIL, OffsetDateTime.now()))
        .isFalse();
  }

  @Test
  @DisplayName("마감(endsAt) 이후에는 ACTIVE여도 재제출할 수 없다")
  void rejectsResubmissionAfterDeadline() {
    ZoneEvent event = buildEvent(ZoneEventStatus.ACTIVE);
    ReflectionTestUtils.setField(event, "startsAt", OffsetDateTime.now().minusHours(3));

    assertThat(event.acceptsResubmission(ParticipationStatus.FAIL, OffsetDateTime.now())).isFalse();
  }

  @Test
  @DisplayName("FAIL이 아닌 참여는 재제출할 수 없다")
  void rejectsResubmissionWhenParticipationNotFailed() {
    ZoneEvent event = buildEvent(ZoneEventStatus.ACTIVE);

    assertThat(event.acceptsResubmission(ParticipationStatus.SUCCESS, OffsetDateTime.now()))
        .isFalse();
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
