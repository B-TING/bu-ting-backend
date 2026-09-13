package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;

/** 상세용 인증 타겟 전체. 예시 이미지는 요청 시점 presigned URL. */
public record AuthTargetDetailResDto(
    String targetId,
    String targetKind,
    String landmarkId,
    String placeName,
    String guideText,
    String exampleImageUrl,
    Double latitude,
    Double longitude,
    Integer radiusM) {

  public static AuthTargetDetailResDto from(ZoneEventAuthTarget target, String exampleImageUrl) {
    if (target == null) {
      return null;
    }
    return new AuthTargetDetailResDto(
        target.getId().toString(),
        target.getTargetKind().name(),
        target.getLandmarkId(),
        target.getPlaceName(),
        target.getGuideText(),
        exampleImageUrl,
        target.getLatitude(),
        target.getLongitude(),
        target.getRadiusM());
  }
}
