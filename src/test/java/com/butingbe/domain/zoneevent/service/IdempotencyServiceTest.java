package com.butingbe.domain.zoneevent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.support.AbstractContainerTest;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class IdempotencyServiceTest extends AbstractContainerTest {

  @Autowired private IdempotencyService idempotencyService;

  @Test
  @DisplayName("키가 없으면 빈 Optional을 돌려준다")
  void noRecordYieldsEmpty() {
    assertThat(idempotencyService.findReplay("missing-key", "ep", "fp")).isEmpty();
  }

  @Test
  @DisplayName("저장 후 같은 키·엔드포인트·지문이면 저장된 JSON을 그대로 돌려준다")
  void saveThenReplay() {
    idempotencyService.save("key-1", "zone-event-review-approve", "fp-1", new Sample("a", 1));

    Optional<String> replay = idempotencyService.findReplay("key-1", "zone-event-review-approve", "fp-1");

    assertThat(replay).isPresent();
    assertThat(replay.get()).contains("\"name\":\"a\"").contains("\"value\":1");
  }

  @Test
  @DisplayName("바디가 null이면 빈 문자열로 저장되고 재조회 시 빈 Optional이 아니라 빈 문자열이다")
  void saveNullBody() {
    idempotencyService.save("key-2", "zone-event-review-reject", "fp-2", null);

    assertThat(idempotencyService.findReplay("key-2", "zone-event-review-reject", "fp-2"))
        .contains("");
  }

  @Test
  @DisplayName("같은 키인데 엔드포인트나 지문이 다르면 409다")
  void mismatchedReplayConflicts() {
    idempotencyService.save("key-3", "zone-event-review-approve", "fp-3", new Sample("a", 1));

    assertThatThrownBy(() -> idempotencyService.findReplay("key-3", "zone-event-review-approve", "different-fp"))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> idempotencyService.findReplay("key-3", "zone-event-review-reject", "fp-3"))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  @DisplayName("키가 비어 있으면 저장·조회 모두 아무 일도 하지 않는다")
  void blankKeyIsNoop() {
    idempotencyService.save(null, "zone-event-review-approve", "fp", new Sample("a", 1));
    idempotencyService.save("", "zone-event-review-approve", "fp", new Sample("a", 1));

    assertThat(idempotencyService.findReplay(null, "zone-event-review-approve", "fp")).isEmpty();
    assertThat(idempotencyService.findReplay("", "zone-event-review-approve", "fp")).isEmpty();
  }

  private record Sample(String name, int value) {}
}
