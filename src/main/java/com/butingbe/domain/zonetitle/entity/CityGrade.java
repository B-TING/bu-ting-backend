package com.butingbe.domain.zonetitle.entity;

import java.util.Map;

/**
 * 도시 등급. 전 구역 칭호 보유 현황으로 계산한다(FR-TTL-06).
 *
 * <p>EXPLORER=서로 다른 구역 T1 3개, MASTER=T3 2개, TRUE_BUSAN=전 구역 T2 이상 또는 T3 4개. 순위는 enum 순서(ordinal)로
 * 비교한다.
 */
public enum CityGrade {
  BEGINNER("초보 여행자", "서로 다른 구역 칭호 3개"),
  EXPLORER("부산 탐험가", "구역 마스터 칭호 2개"),
  MASTER("부산 마스터", "전 구역 T2 이상 또는 마스터 칭호 4개"),
  TRUE_BUSAN("찐부산인", null);

  private final String gradeName;
  private final String nextCondition;

  CityGrade(String gradeName, String nextCondition) {
    this.gradeName = gradeName;
    this.nextCondition = nextCondition;
  }

  public String gradeName() {
    return gradeName;
  }

  /** 다음 등급으로 오르기 위한 조건. 최고 등급이면 null. */
  public String nextCondition() {
    return nextCondition;
  }

  public CityGrade next() {
    return this == TRUE_BUSAN ? null : values()[ordinal() + 1];
  }

  /** 구역별 최고 tier 맵으로부터 도시 등급을 계산한다. */
  public static CityGrade from(Map<String, Integer> maxTierByZone) {
    long distinctZones = maxTierByZone.values().stream().filter(tier -> tier >= 1).count();
    long tier3Zones = maxTierByZone.values().stream().filter(tier -> tier >= 3).count();
    long tier2OrMoreZones = maxTierByZone.values().stream().filter(tier -> tier >= 2).count();

    if (tier2OrMoreZones >= 6 || tier3Zones >= 4) {
      return TRUE_BUSAN;
    }
    if (tier3Zones >= 2) {
      return MASTER;
    }
    if (distinctZones >= 3) {
      return EXPLORER;
    }
    return BEGINNER;
  }
}
