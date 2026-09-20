package com.butingbe.domain.place.entity;

import com.butingbe.domain.chat.entity.ChatZone;
import java.util.Arrays;
import java.util.Optional;

/**
 * 법정동 구·군 코드로 부산 권역을 찾는다.
 *
 * <p>TourAPI의 {@code lDongSignguCd}가 {@link ChatZone#getCityCodes()}와 같은 코드 체계라 별도 매핑 테이블 없이 그대로
 * 대조한다. 부산 밖이거나 코드가 없으면 권역을 비워 둔다.
 */
public final class PlaceZoneResolver {

  private PlaceZoneResolver() {}

  public static Optional<ChatZone> resolve(String districtCode) {
    if (districtCode == null || districtCode.isBlank()) {
      return Optional.empty();
    }
    String code = districtCode.trim();
    return Arrays.stream(ChatZone.values())
        .filter(zone -> zone.getCityCodes().contains(code))
        .findFirst();
  }
}
