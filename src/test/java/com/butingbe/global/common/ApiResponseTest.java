package com.butingbe.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

  @Test
  @DisplayName("성공 응답은 success=true와 데이터를 담는다")
  void success() {
    ApiResponse<String> response = ApiResponse.success("ok", "value");

    assertThat(response.isSuccess()).isTrue();
    assertThat(response.getMessage()).isEqualTo("ok");
    assertThat(response.getData()).isEqualTo("value");
  }

  @Test
  @DisplayName("데이터 없는 실패 응답은 data가 null이다")
  void failWithoutData() {
    ApiResponse<Void> response = ApiResponse.fail("nope");

    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getMessage()).isEqualTo("nope");
    assertThat(response.getData()).isNull();
  }

  @Test
  @DisplayName("참조 데이터를 담은 실패 응답은 success=false로 데이터를 함께 내려준다")
  void failWithData() {
    Map<String, String> reference = Map.of("participationId", "abc");
    ApiResponse<Map<String, String>> response = ApiResponse.fail("already open", reference);

    assertThat(response.isSuccess()).isFalse();
    assertThat(response.getMessage()).isEqualTo("already open");
    assertThat(response.getData()).isEqualTo(reference);
  }
}
