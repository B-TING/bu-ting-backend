package com.butingbe.domain.route.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.travel.entity.TransportType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 캐시 키가 값으로 비교돼야 같은 구간이 같은 행으로 모인다. */
class PlaceTravelTimeIdTest {

  private static final String FROM = "35.09750,129.01070";
  private static final String TO = "35.09660,129.03060";

  @Test
  @DisplayName("좌표와 교통수단이 같으면 같은 키다")
  void equalsByValue() {
    PlaceTravelTimeId one = new PlaceTravelTimeId(FROM, TO, TransportType.PUBLIC_TRANSPORT);
    PlaceTravelTimeId other = new PlaceTravelTimeId(FROM, TO, TransportType.PUBLIC_TRANSPORT);

    assertThat(one).isEqualTo(other).hasSameHashCodeAs(other).isEqualTo(one);
  }

  @Test
  @DisplayName("방향이나 교통수단이 다르면 다른 키다")
  void differsByDirectionAndTransport() {
    PlaceTravelTimeId base = new PlaceTravelTimeId(FROM, TO, TransportType.PUBLIC_TRANSPORT);

    assertThat(base)
        .isNotEqualTo(new PlaceTravelTimeId(TO, FROM, TransportType.PUBLIC_TRANSPORT))
        .isNotEqualTo(new PlaceTravelTimeId(FROM, TO, TransportType.WALK))
        .isNotEqualTo("35.09750,129.01070");
  }
}
