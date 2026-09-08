package com.butingbe.domain.zoneevent.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ZoneEventAuthTargetTest {

  @Test
  @DisplayName("생성 시 소스 좌표를 생략하면 관리자 좌표를 그대로 원본으로 스냅샷한다")
  void defaultsSourceCoordinatesToGivenCoordinates() {
    ZoneEventAuthTarget target =
        ZoneEventAuthTarget.builder()
            .targetKind(ZoneEventTargetKind.PLACE)
            .placeName("해운대 해수욕장")
            .latitude(35.1587)
            .longitude(129.1604)
            .radiusM(100)
            .build();

    assertThat(target.getSourceLatitude()).isEqualTo(35.1587);
    assertThat(target.getSourceLongitude()).isEqualTo(129.1604);
    assertThat(target.isCoordinatesOverridden()).isFalse();
    assertThat(target.getStatus()).isEqualTo(ZoneEventTargetStatus.ACTIVE);
  }

  @Test
  @DisplayName("관리자가 좌표를 수정하면 원본과 달라져 override로 표시된다")
  void marksOverriddenWhenAdminChangesCoordinates() {
    ZoneEventAuthTarget target =
        ZoneEventAuthTarget.builder()
            .targetKind(ZoneEventTargetKind.PLACE)
            .placeName("해운대 해수욕장")
            .sourceLatitude(35.1587)
            .sourceLongitude(129.1604)
            .latitude(35.16)
            .longitude(129.1604)
            .radiusM(100)
            .build();

    assertThat(target.isCoordinatesOverridden()).isTrue();
  }

  @Test
  @DisplayName("취소하면 CANCELLED 상태가 된다")
  void cancelMarksCancelled() {
    ZoneEventAuthTarget target =
        ZoneEventAuthTarget.builder()
            .targetKind(ZoneEventTargetKind.PLACE)
            .placeName("해운대 해수욕장")
            .latitude(35.1587)
            .longitude(129.1604)
            .radiusM(100)
            .build();

    target.cancel();

    assertThat(target.getStatus()).isEqualTo(ZoneEventTargetStatus.CANCELLED);
  }

  @Test
  @DisplayName("ACTIVE가 아닌 타겟은 취소할 수 없다")
  void cancelNonActiveTargetConflicts() {
    ZoneEventAuthTarget target =
        ZoneEventAuthTarget.builder()
            .targetKind(ZoneEventTargetKind.PLACE)
            .placeName("해운대 해수욕장")
            .latitude(35.1587)
            .longitude(129.1604)
            .radiusM(100)
            .build();
    target.cancel();

    assertThatThrownBy(target::cancel)
        .isInstanceOf(com.butingbe.global.error.exception.ConflictException.class);
  }

  @Test
  @DisplayName("긴급 교체되면 REPLACED 상태가 된다")
  void markReplacedSetsReplacedStatus() {
    ZoneEventAuthTarget target =
        ZoneEventAuthTarget.builder()
            .targetKind(ZoneEventTargetKind.PLACE)
            .placeName("해운대 해수욕장")
            .latitude(35.1587)
            .longitude(129.1604)
            .radiusM(100)
            .build();

    target.markReplaced();

    assertThat(target.getStatus()).isEqualTo(ZoneEventTargetStatus.REPLACED);
  }

  @Test
  @DisplayName("ACTIVE가 아닌 타겟은 교체 표시할 수 없다")
  void markReplacedNonActiveTargetConflicts() {
    ZoneEventAuthTarget target =
        ZoneEventAuthTarget.builder()
            .targetKind(ZoneEventTargetKind.PLACE)
            .placeName("해운대 해수욕장")
            .latitude(35.1587)
            .longitude(129.1604)
            .radiusM(100)
            .build();
    target.cancel();

    assertThatThrownBy(target::markReplaced)
        .isInstanceOf(com.butingbe.global.error.exception.ConflictException.class);
  }
}
