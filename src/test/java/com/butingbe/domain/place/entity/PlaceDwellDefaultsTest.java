package com.butingbe.domain.place.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PlaceDwellDefaultsTest {

  @ParameterizedTest
  @DisplayName("관광 유형별 기본 체류 시간을 준다")
  @CsvSource({"12, 90", "14, 60", "15, 120", "25, 180", "28, 120", "32, 30", "38, 60", "39, 60"})
  void returnsDefaultByContentType(String contentTypeId, int expected) {
    assertThat(PlaceDwellDefaults.forContentType(contentTypeId)).isEqualTo(expected);
  }

  @ParameterizedTest
  @DisplayName("모르는 유형이나 값이 없으면 60분으로 둔다")
  @NullSource
  @ValueSource(strings = {"99", "", "   "})
  void fallsBackToSixtyMinutes(String contentTypeId) {
    assertThat(PlaceDwellDefaults.forContentType(contentTypeId)).isEqualTo(60);
  }

  @ParameterizedTest
  @DisplayName("앞뒤 공백이 있어도 매핑한다")
  @ValueSource(strings = {" 12 ", "12 "})
  void trimsContentTypeId(String contentTypeId) {
    assertThat(PlaceDwellDefaults.forContentType(contentTypeId)).isEqualTo(90);
  }
}
