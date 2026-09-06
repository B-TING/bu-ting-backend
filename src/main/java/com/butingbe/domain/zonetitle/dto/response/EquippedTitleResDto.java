package com.butingbe.domain.zonetitle.dto.response;

import com.butingbe.domain.zonetitle.entity.UserZoneTitle;

/** 장착 칭호 요약. 앨범·댓글·프로필 응답에 포함된다(FR-TTL-04). */
public record EquippedTitleResDto(
    String titleCode, String titleName, String zoneId, int tier, String style, String color) {

  public static EquippedTitleResDto from(UserZoneTitle title) {
    return new EquippedTitleResDto(
        title.getTitleDef().getTitleCode(),
        title.getTitleDef().getTitleName(),
        title.getZoneId(),
        title.getTitleDef().getTier(),
        title.getTitleDef().getStyle(),
        title.getTitleDef().getColor());
  }
}
