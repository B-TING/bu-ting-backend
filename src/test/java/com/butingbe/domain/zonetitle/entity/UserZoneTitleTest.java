package com.butingbe.domain.zonetitle.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserZoneTitleTest {

  @Test
  @DisplayName("장착·해제·회수(멱등)를 처리한다")
  void equipUnequipRevoke() {
    UserZoneTitle title =
        UserZoneTitle.builder()
            .userId(UUID.randomUUID())
            .titleDef(null)
            .zoneId("SUYEONG_NAMGU")
            .equipped(false)
            .build();

    title.equip();
    assertThat(title.getEquipped()).isTrue();
    title.unequip();
    assertThat(title.getEquipped()).isFalse();

    OffsetDateTime first = OffsetDateTime.now();
    title.revoke(first);
    title.revoke(first.plusHours(1)); // 멱등
    assertThat(title.getRevokedAt()).isEqualTo(first);
  }
}
