package com.butingbe.domain.zonetitle.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CityGradeTest {

  @Test
  @DisplayName("칭호가 없으면 초보 여행자다")
  void beginner() {
    assertThat(CityGrade.from(Map.of())).isEqualTo(CityGrade.BEGINNER);
  }

  @Test
  @DisplayName("서로 다른 구역 T1 3개면 탐험가다")
  void explorer() {
    assertThat(CityGrade.from(Map.of("A", 1, "B", 1, "C", 1))).isEqualTo(CityGrade.EXPLORER);
  }

  @Test
  @DisplayName("T3 구역 2개면 마스터다")
  void master() {
    assertThat(CityGrade.from(Map.of("A", 3, "B", 3, "C", 1))).isEqualTo(CityGrade.MASTER);
  }

  @Test
  @DisplayName("전 구역 T2 이상이면 찐부산인이다")
  void trueBusanByAllT2() {
    Map<String, Integer> all = Map.of("A", 2, "B", 2, "C", 2, "D", 2, "E", 2, "F", 2);
    assertThat(CityGrade.from(all)).isEqualTo(CityGrade.TRUE_BUSAN);
  }

  @Test
  @DisplayName("T3 구역 4개면 찐부산인이다")
  void trueBusanByFourT3() {
    assertThat(CityGrade.from(Map.of("A", 3, "B", 3, "C", 3, "D", 3)))
        .isEqualTo(CityGrade.TRUE_BUSAN);
  }

  @Test
  @DisplayName("다음 등급과 조건, 최고 등급의 next는 null")
  void nextGrade() {
    assertThat(CityGrade.BEGINNER.next()).isEqualTo(CityGrade.EXPLORER);
    assertThat(CityGrade.BEGINNER.nextCondition()).isNotNull();
    assertThat(CityGrade.TRUE_BUSAN.next()).isNull();
    assertThat(CityGrade.TRUE_BUSAN.nextCondition()).isNull();
    assertThat(CityGrade.EXPLORER.gradeName()).isEqualTo("부산 탐험가");
  }
}
