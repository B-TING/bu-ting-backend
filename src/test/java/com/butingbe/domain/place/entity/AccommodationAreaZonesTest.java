package com.butingbe.domain.place.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.chat.entity.ChatZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AccommodationAreaZonesTest {

  @ParameterizedTest
  @DisplayName("위저드 지역 아이디를 권역으로 옮긴다")
  @CsvSource({
    "haeundae, HAEUNDAE_GIJANG",
    "gwangan, SUYEONG_NAMGU",
    "seomyeon, CENTRAL_NORTH",
    "nampo, OLD_DOWNTOWN",
    "yeongdo, YEONGDO"
  })
  void resolvesWizardAreaId(String areaId, ChatZone expected) {
    assertThat(AccommodationAreaZones.resolve(areaId)).contains(expected);
  }

  @ParameterizedTest
  @DisplayName("권역 이름을 그대로 보내도 받는다")
  @ValueSource(strings = {"WESTERN_BUSAN", "western_busan", " WESTERN_BUSAN "})
  void resolvesZoneName(String value) {
    assertThat(AccommodationAreaZones.resolve(value)).contains(ChatZone.WESTERN_BUSAN);
  }

  @ParameterizedTest
  @DisplayName("대소문자와 공백 차이를 흡수한다")
  @ValueSource(strings = {"HAEUNDAE", " Haeundae "})
  void ignoresCaseAndWhitespace(String value) {
    assertThat(AccommodationAreaZones.resolve(value)).contains(ChatZone.HAEUNDAE_GIJANG);
  }

  @ParameterizedTest
  @DisplayName("해석하지 못하는 값은 비워 둔다 — 일정 생성을 막지 않는다")
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "gijang", "서울"})
  void returnsEmptyForUnknownArea(String value) {
    assertThat(AccommodationAreaZones.resolve(value)).isEmpty();
  }
}
