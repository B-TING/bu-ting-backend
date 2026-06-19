package com.butingbe.domain.user.dto.request;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignUpReqDtoTest {

  @Test
  @DisplayName("이름 값이 null이면 빈 문자열로 변환되지만 Name 검증에서 예외가 발생한다")
  void toEmbeddedNameFallsBackToEmptyText() {
    SignUpReqDto request =
        new SignUpReqDto("signup@example.com", "가입유저", "google", "google-123", null, null);

    assertThatThrownBy(request::toEmbeddedName).isInstanceOf(IllegalArgumentException.class);
  }
}
