package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 활성 이벤트 목록 항목.
 *
 * <p>개인화 필드({@code myParticipationStatus}, {@code myOpenParticipationId})는 비로그인·미참여 시 null이다.
 */
public record ZoneEventSummaryResDto(
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
    AuthTargetBriefResDto authTarget,
    long successCount,
    String myParticipationStatus,
    UUID myOpenParticipationId) {

  public static ZoneEventSummaryResDto of(
      ZoneEvent event,
      ZoneEventAuthTarget target,
      long remainingSeconds,
      long successCount,
      String myParticipationStatus,
      UUID myOpenParticipationId) {
    return new ZoneEventSummaryResDto(
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
        AuthTargetBriefResDto.from(target),
        successCount,
        myParticipationStatus,
        myOpenParticipationId);
  }
}
