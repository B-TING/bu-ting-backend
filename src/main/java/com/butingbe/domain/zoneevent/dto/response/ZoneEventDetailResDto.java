package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventAuthTarget;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 이벤트 상세. 목록 항목에 촬영 가이드·예시 이미지·우수 보상·성공 참여자 수·남은 참여 가능 횟수를 더한다.
 *
 * <p>{@code targets}는 이 이벤트의 ACTIVE 타겟 전체(참여·제출 시 targetId로 선택). {@code deadline}은 {@code endsAt}과
 * 같은 값을 재제출 UI 용도로 명시적 이름으로 내려준다. {@code myParticipation}은 비로그인 시 null이다. {@code round}는 Phase 2에서
 * 채워진다.
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
    List<AuthTargetDetailResDto> targets,
    String slotCode,
    OffsetDateTime deadline,
    long successCount,
    Integer successLimitPerUser,
    Integer myRemainingAttempts,
    MyParticipationResDto myParticipation,
    Object round) {

  public static ZoneEventDetailResDto of(
      ZoneEvent event,
      ZoneEventAuthTarget target,
      List<AuthTargetDetailResDto> targets,
      String exampleImageUrl,
      long remainingSeconds,
      long successCount,
      Integer myRemainingAttempts,
      MyParticipationResDto myParticipation) {
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
        targets,
        event.getSlotCode(),
        event.endsAt(),
        successCount,
        event.getSuccessLimitPerUser(),
        myRemainingAttempts,
        myParticipation,
        null);
  }
}
