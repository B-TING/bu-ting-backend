package com.butingbe.domain.place.entity;

import java.util.Map;

/**
 * 관광 유형별 기본 체류 시간(분).
 *
 * <p>체류 시간은 TourAPI도 Google Places도 주지 않는다. 시간 기반 일정 배치에는 반드시 필요한 값이라 유형별 기본값으로 먼저 채우고, 인기 장소만 운영에서
 * 실측값으로 보정한다. 전체를 수기로 채우려 하면 끝나지 않는다.
 *
 * <p>키는 TourAPI의 {@code contentTypeId}다.
 */
public final class PlaceDwellDefaults {

  private static final int FALLBACK_MINUTES = 60;

  private static final Map<String, Integer> BY_CONTENT_TYPE =
      Map.of(
          "12", 90, // 관광지 — 마을·거리 단위가 많아 이동과 둘러보기를 함께 잡는다
          "14", 60, // 문화시설
          "15", 120, // 축제·공연·행사
          "25", 180, // 여행코스 — 그 자체가 여러 장소를 묶은 단위다
          "28", 120, // 레포츠
          "32", 30, // 숙박 — 체크인 경유만 고려한다
          "38", 60, // 쇼핑
          "39", 60); // 음식점

  private PlaceDwellDefaults() {}

  /** 유형을 모르면 60분으로 둔다. 배치가 아예 불가능해지는 것보다 낫다. */
  public static int forContentType(String contentTypeId) {
    if (contentTypeId == null) {
      return FALLBACK_MINUTES;
    }
    return BY_CONTENT_TYPE.getOrDefault(contentTypeId.trim(), FALLBACK_MINUTES);
  }
}
