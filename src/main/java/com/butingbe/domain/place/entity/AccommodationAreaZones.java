package com.butingbe.domain.place.entity;

import com.butingbe.domain.chat.entity.ChatZone;
import java.util.Map;
import java.util.Optional;

/**
 * 위저드의 숙소 지역 선택을 부산 권역으로 옮긴다.
 *
 * <p>앱은 화면용 지역 아이디({@code haeundae} 등)를 보내고 서버는 {@link ChatZone} 이름으로 후보를 좁힌다. 둘의 이름이 겹치지 않아 그대로는
 * 매칭되지 않으므로 여기서 이어 준다. 앱이 권역 이름을 직접 보내는 경우도 계속 받는다.
 *
 * <p>모르는 값은 예외로 만들지 않고 비워 둔다. 숙소 지역은 후보를 좁히는 힌트일 뿐이라, 해석하지 못했다고 일정 생성을 막을 이유가 없다.
 */
public final class AccommodationAreaZones {

  private static final Map<String, ChatZone> BY_WIZARD_AREA_ID =
      Map.of(
          "haeundae", ChatZone.HAEUNDAE_GIJANG,
          "gwangan", ChatZone.SUYEONG_NAMGU,
          "seomyeon", ChatZone.CENTRAL_NORTH,
          "nampo", ChatZone.OLD_DOWNTOWN,
          "yeongdo", ChatZone.YEONGDO);

  private AccommodationAreaZones() {}

  public static Optional<ChatZone> resolve(String accommodationArea) {
    if (accommodationArea == null || accommodationArea.isBlank()) {
      return Optional.empty();
    }
    String value = accommodationArea.trim();
    ChatZone byWizardId = BY_WIZARD_AREA_ID.get(value.toLowerCase(java.util.Locale.ROOT));
    if (byWizardId != null) {
      return Optional.of(byWizardId);
    }
    try {
      return Optional.of(ChatZone.fromString(value));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
