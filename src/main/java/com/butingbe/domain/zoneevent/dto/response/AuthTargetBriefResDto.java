package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;

/** 활성 목록용 인증 타겟 요약(표시명·좌표·반경). */
public record AuthTargetBriefResDto(
    String placeName, Double latitude, Double longitude, Integer radiusM) {

  public static AuthTargetBriefResDto from(ZoneEventAuthTarget target) {
    if (target == null) {
      return null;
    }
    return new AuthTargetBriefResDto(
        target.getPlaceName(), target.getLatitude(), target.getLongitude(), target.getRadiusM());
  }
}
