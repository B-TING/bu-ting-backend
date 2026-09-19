package com.butingbe.domain.place.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.chat.entity.ChatZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PlaceZoneResolverTest {

  @ParameterizedTest
  @DisplayName("구·군 코드를 부산 6권역으로 매핑한다")
  @CsvSource({
    "26350, HAEUNDAE_GIJANG", // 해운대구
    "26710, HAEUNDAE_GIJANG", // 기장군
    "26500, SUYEONG_NAMGU", // 수영구
    "26230, CENTRAL_NORTH", // 부산진구
    "26110, OLD_DOWNTOWN", // 중구
    "26200, YEONGDO", // 영도구
    "26380, WESTERN_BUSAN" // 사하구
  })
  void resolvesDistrictCode(String districtCode, ChatZone expected) {
    assertThat(PlaceZoneResolver.resolve(districtCode)).contains(expected);
  }

  @Test
  @DisplayName("앞뒤 공백이 있어도 매핑한다")
  void trimsDistrictCode() {
    assertThat(PlaceZoneResolver.resolve(" 26350 ")).contains(ChatZone.HAEUNDAE_GIJANG);
  }

  @ParameterizedTest
  @DisplayName("부산 밖 코드는 권역이 없다")
  @ValueSource(strings = {"11110", "26999"})
  void returnsEmptyForUnknownDistrict(String districtCode) {
    assertThat(PlaceZoneResolver.resolve(districtCode)).isEmpty();
  }

  @ParameterizedTest
  @DisplayName("코드가 비어 있으면 권역이 없다")
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void returnsEmptyForBlankDistrict(String districtCode) {
    assertThat(PlaceZoneResolver.resolve(districtCode)).isEmpty();
  }
}
