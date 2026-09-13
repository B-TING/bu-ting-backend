package com.butingbe.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoggingPushSenderTest {

  @Test
  @DisplayName("로깅 sender는 전송한 토큰 수를 돌려준다")
  void returnsRecipientCount() {
    assertThat(new LoggingPushSender().send(List.of("a", "b"), "제목", "본문")).isEqualTo(2);
  }
}
