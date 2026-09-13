package com.butingbe.domain.zonetitle.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/** 내 칭호 보유·진행도. cityGrade는 도시 등급 이슈(#202)에서 채워진다. */
public record MyZoneTitlesResDto(
    EquippedTitleResDto equipped, Object cityGrade, List<ZoneProgress> zones) {

  public record ZoneProgress(
      String zoneId,
      long successCount,
      int currentTier,
      Integer nextTier,
      Integer remainingToNext,
      List<TitleItem> titles) {}

  public record TitleItem(
      String userTitleId,
      String titleCode,
      String titleName,
      int tier,
      String style,
      String color,
      OffsetDateTime earnedAt,
      boolean equipped) {}
}
