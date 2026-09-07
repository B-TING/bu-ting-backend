package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 참여 로그 응답.
 *
 * <p>참여 시작 시점에는 {@code distanceM}(타겟까지 거리)을 채우고 미디어·완료 시각·보상은 비어 있다. 후속 이슈(제출·판정)에서 채워진다.
 */
public record ParticipationResDto(
    String participationId,
    String eventId,
    String zoneId,
    String typeCode,
    String status,
    Boolean success,
    Integer distanceM,
    String mediaUrl,
    String content,
    long likeCount,
    String visibility,
    OffsetDateTime joinedAt,
    OffsetDateTime completedAt,
    List<Object> rewards) {

  public static ParticipationResDto of(ZoneEventParticipation participation, Integer distanceM) {
    return new ParticipationResDto(
        participation.getId().toString(),
        participation.getEvent().getId().toString(),
        participation.getEvent().getZoneId(),
        participation.getEvent().getType().getTypeCode(),
        participation.getStatus().name(),
        participation.getSuccess(),
        distanceM,
        null,
        participation.getContent(),
        participation.getLikeCount(),
        participation.getVisibility().name(),
        participation.getJoinedAt(),
        participation.getCompletedAt(),
        List.of());
  }
}
