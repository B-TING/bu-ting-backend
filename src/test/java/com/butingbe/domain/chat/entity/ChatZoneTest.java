package com.butingbe.domain.chat.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ChatZoneTest {

  @ParameterizedTest
  @EnumSource(ChatZone.class)
  @DisplayName("ChatZone의 모든 상수에 대해 Getter 메서드 로직 및 데이터 정합성을 검증한다")
  void enumGettersAndDataIntegrityTest(ChatZone chatZone) {
    // When & Then
    // 💡 모든 Enum 상수를 순회하며 Getter를 호출하므로 롬복 Getter 커버리지가 100% 채워집니다.
    assertThat(chatZone.getZoneName()).isNotBlank();
    assertThat(chatZone.getCityCodes()).isNotEmpty();
    assertThat(chatZone.getLandmarks()).isNotEmpty();

    // 특정 권역 데이터 샘플 검증 (정합성 체크)
    if (chatZone == ChatZone.SUYEONG_NAMGU) {
      assertThat(chatZone.getZoneName()).isEqualTo("수영구+남구");
      assertThat(chatZone.getCityCodes()).containsExactly("26500", "26290");
      assertThat(chatZone.getLandmarks()).contains("광안리 해수욕장");
    }
  }

  @Test
  @DisplayName("fromString 메서드에 유효한 문자열을 대소문자 구분 없이 던지면 매핑되는 상수를 반환한다")
  void fromString_success() {
    // Given
    String lowercaseValue = "suyeong_namgu";
    String uppercaseValue = "HAEUNDAE_GIJANG";

    // When
    ChatZone result1 = ChatZone.fromString(lowercaseValue);
    ChatZone result2 = ChatZone.fromString(uppercaseValue);

    // Then
    // 💡 문자열 매핑 로직 라인 커버리지 충족
    assertThat(result1).isEqualTo(ChatZone.SUYEONG_NAMGU);
    assertThat(result2).isEqualTo(ChatZone.HAEUNDAE_GIJANG);
  }

  @Test
  @DisplayName("fromString 메서드에 존재하지 않는 권역 문자열을 던지면 IllegalArgumentException 예외가 발생한다")
  void fromString_throwsException_whenInvalidValue() {
    // Given
    String invalidValue = "SEOUL_GANGNAM";

    // When & Then
    // 💡 .orElseThrow() 내부의 예외 발생 분기문을 실행시켜 JaCoCo 분기 커버리지를 완벽히 채웁니다.
    assertThatThrownBy(() -> ChatZone.fromString(invalidValue))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("존재하지 않는 채팅 권역입니다: " + invalidValue);
  }
}
