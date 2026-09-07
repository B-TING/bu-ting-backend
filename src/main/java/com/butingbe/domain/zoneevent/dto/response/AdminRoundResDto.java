package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.RoundType;
import com.butingbe.domain.zoneevent.entity.ZoneEventBackupTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import java.time.OffsetDateTime;
import java.util.List;

/** 운영 회차 상세: 회차 + 슬롯(집계 포함) + 예비 타겟. */
public record AdminRoundResDto(
    String roundId,
    Integer roundNo,
    String name,
    RoundType roundType,
    RoundStatus status,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    String timezone,
    OffsetDateTime closedAt,
    OffsetDateTime settledAt,
    boolean settled,
    String cancelReason,
    RewardSnapshot excellenceReward,
    Long revision,
    List<Slot> slots,
    List<Backup> backups) {

  public record Slot(
      String slotId,
      String slotKind,
      String zoneId,
      String eventId,
      long participantCount,
      long successCount,
      long underReviewCount) {}

  public record Backup(String targetId, String placeName, Double latitude, Double longitude) {}

  public static AdminRoundResDto of(
      ZoneEventRound round,
      List<ZoneEventRoundSlot> slots,
      List<ZoneEventBackupTarget> backups,
      java.util.Map<String, long[]> countsByEventId) {
    return new AdminRoundResDto(
        round.getId().toString(),
        round.getRoundNo(),
        round.getName(),
        round.getRoundType(),
        round.getStatus(),
        round.getStartsAt(),
        round.getEndsAt(),
        round.getTimezone(),
        round.getClosedAt(),
        round.getSettledAt(),
        round.getStatus() == RoundStatus.SETTLED,
        round.getCancelReason(),
        round.getExcellenceReward(),
        round.getRevision(),
        slots.stream()
            .map(
                s -> {
                  String eventId = s.getEventId() == null ? null : s.getEventId().toString();
                  long[] counts = eventId == null ? new long[] {0, 0, 0} : countsByEventId.getOrDefault(eventId, new long[] {0, 0, 0});
                  return new Slot(
                      s.getId().toString(), s.getSlotKind().name(), s.getZoneId(), eventId, counts[0], counts[1], counts[2]);
                })
            .toList(),
        backups.stream()
            .map(b -> new Backup(b.getId().toString(), b.getPlaceName(), b.getLatitude(), b.getLongitude()))
            .toList());
  }
}
