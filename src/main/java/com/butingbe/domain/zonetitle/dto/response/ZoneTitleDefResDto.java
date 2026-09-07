package com.butingbe.domain.zonetitle.dto.response;

import com.butingbe.domain.zonetitle.entity.ZoneTitleDef;

/** 칭호 정의(공개). */
public record ZoneTitleDefResDto(
    String zoneId,
    int tier,
    int requiredSuccessCount,
    String titleCode,
    String titleName,
    String style,
    String color) {

  public static ZoneTitleDefResDto from(ZoneTitleDef def) {
    return new ZoneTitleDefResDto(
        def.getZoneId(),
        def.getTier(),
        def.getRequiredSuccessCount(),
        def.getTitleCode(),
        def.getTitleName(),
        def.getStyle(),
        def.getColor());
  }
}
