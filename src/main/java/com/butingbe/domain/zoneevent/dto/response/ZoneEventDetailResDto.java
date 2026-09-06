package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 이벤트 상세. 목록 항목에 촬영 가이드·예시 이미지·우수 보상·성공 참여자 수·남은 참여 가능 횟수를 더한다.
 *
 * <p>{@code myRemainingAttempts}는 비로그인 시 null이다. {@code round}는 Phase 2에서 채워진다.
 */
public record ZoneEventDetailResDto(
    String eventId,
    ZoneRef zone,
    String typeCode,
    String typeName,
    boolean requiresUpload,
    String title,
    String description,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    Integer durationMinutes,
    long remainingSeconds,
    String status,
    UUID roundId,
    RewardSummaryResDto baseReward,
    RewardSummaryResDto excellenceReward,
    AuthTargetDetailResDto authTarget,
    long successCount,
    Integer successLimitPerUser,
    Integer myRemainingAttempts,
    Object round) {

  public static ZoneEventDetailResDto of(
      ZoneEvent event,
      ZoneEventAuthTarget target,
      String exampleImageUrl,
      long remainingSeconds,
      long successCount,
      Integer myRemainingAttempts) {
    return new ZoneEventDetailResDto(
        event.getId().toString(),
        ZoneRef.from(event.getZoneId()),
        event.getType().getTypeCode(),
        event.getType().getName(),
        Boolean.TRUE.equals(event.getType().getRequiresUpload()),
        event.getTitle(),
        event.getDescription(),
        event.getStartsAt(),
        event.endsAt(),
        event.getDurationMinutes(),
        remainingSeconds,
        event.getStatus().name(),
        event.getRoundId(),
        RewardSummaryResDto.from(event.getBaseReward()),
        RewardSummaryResDto.from(event.getExcellenceReward()),
        AuthTargetDetailResDto.from(target, exampleImageUrl),
        successCount,
        event.getSuccessLimitPerUser(),
        myRemainingAttempts,
        null);
  }
}
