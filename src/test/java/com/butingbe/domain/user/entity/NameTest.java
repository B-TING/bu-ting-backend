package com.butingbe.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NameTest {

  @Test
  @DisplayName("올바른 성과 이름을 입력하면 Name 객체가 정상 생성된다")
  void createNameSuccess() {
    // given & when
    Name name = new Name(" 홍 ", " 길동 "); // 앞뒤 공백 포함

    // then - .trim()이 잘 작동했는지도 함께 검증
    assertThat(name.getLastName()).isEqualTo("홍");
    assertThat(name.getFirstName()).isEqualTo("길동");
    assertThat(name.getFullName()).isEqualTo("홍길동");
  }

  @ParameterizedTest
  @NullAndEmptySource // 💡 null과 ""(빈값)을 차례대로 주입해 줍니다.
  @ValueSource(strings = {" ", "   "}) // 💡 공백 문자열도 주입해 줍니다.
  @DisplayName("성에 유효하지 않은 값(null, 빈값, 공백)이 들어오면 예외가 발생한다")
  void lastNameValidationFail(String invalidLastName) {
    // when & then
    assertThatThrownBy(() -> new Name(invalidLastName, "길동"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("성은 필수 입력 항목입니다.");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "   "})
  @DisplayName("이름에 유효하지 않은 값(null, 빈값, 공백)이 들어오면 예외가 발생한다")
  void firstNameValidationFail(String invalidFirstName) {
    // when & then
    assertThatThrownBy(() -> new Name("홍", invalidFirstName))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이름은 필수 입력 항목입니다.");
  }
}
