package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 운영용 이벤트 상세. 참여 통계와 인증 타겟 전체를 포함한다. */
public record AdminZoneEventResDto(
    String eventId,
    String zoneId,
    String typeCode,
    String title,
    String description,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    Integer durationMinutes,
    String status,
    UUID roundId,
    Integer successLimitPerUser,
    RewardSnapshot baseReward,
    RewardSnapshot excellenceReward,
    AdminAuthTarget authTarget,
    long joinedCount,
    long successCount) {

  public record AdminAuthTarget(
      String targetId,
      String targetKind,
      String landmarkId,
      String placeName,
      String guideText,
      String exampleFileKey,
      Double latitude,
      Double longitude,
      Integer radiusM) {

    static AdminAuthTarget from(ZoneEventAuthTarget target) {
      if (target == null) {
        return null;
      }
      return new AdminAuthTarget(
          target.getId().toString(),
          target.getTargetKind().name(),
          target.getLandmarkId(),
          target.getPlaceName(),
          target.getGuideText(),
          target.getExampleFileKey(),
          target.getLatitude(),
          target.getLongitude(),
          target.getRadiusM());
    }
  }

  public static AdminZoneEventResDto of(
      ZoneEvent event, ZoneEventAuthTarget target, long joinedCount, long successCount) {
    return new AdminZoneEventResDto(
        event.getId().toString(),
        event.getZoneId(),
        event.getType().getTypeCode(),
        event.getTitle(),
        event.getDescription(),
        event.getStartsAt(),
        event.endsAt(),
        event.getDurationMinutes(),
        event.getStatus().name(),
        event.getRoundId(),
        event.getSuccessLimitPerUser(),
        event.getBaseReward(),
        event.getExcellenceReward(),
        AdminAuthTarget.from(target),
        joinedCount,
        successCount);
  }
}
