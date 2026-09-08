package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;

/** 운영용 인증 타겟 전체 상세. 원본 좌표와 관리자 수정 좌표를 함께 내려준다. */
public record AdminAuthTargetResDto(
    String targetId,
    String eventId,
    String targetKind,
    String landmarkId,
    String placeContentId,
    String contentTypeId,
    String placeName,
    String guideText,
    String exampleFileKey,
    Double sourceLatitude,
    Double sourceLongitude,
    Double latitude,
    Double longitude,
    boolean coordinatesOverridden,
    Integer radiusM,
    String status,
    Long revision) {

  public static AdminAuthTargetResDto from(ZoneEventAuthTarget target) {
    return new AdminAuthTargetResDto(
        target.getId().toString(),
        target.getEvent().getId().toString(),
        target.getTargetKind().name(),
        target.getLandmarkId(),
        target.getPlaceContentId(),
        target.getContentTypeId(),
        target.getPlaceName(),
        target.getGuideText(),
        target.getExampleFileKey(),
        target.getSourceLatitude(),
        target.getSourceLongitude(),
        target.getLatitude(),
        target.getLongitude(),
        target.isCoordinatesOverridden(),
        target.getRadiusM(),
        target.getStatus().name(),
        target.getRevision());
  }
}
