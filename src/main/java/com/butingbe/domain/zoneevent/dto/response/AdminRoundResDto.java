package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.RoundType;
import com.butingbe.domain.zoneevent.entity.ZoneEventBackupTarget;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import java.time.OffsetDateTime;
import java.util.List;

/** 운영 회차 상세: 회차 + 슬롯 + 예비 타겟. */
public record AdminRoundResDto(
    String roundId,
    RoundType roundType,
    RoundStatus status,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    String timezone,
    OffsetDateTime settledAt,
    List<Slot> slots,
    List<Backup> backups) {

  public record Slot(String slotId, String slotKind, String zoneId, String eventId) {}

  public record Backup(String targetId, String placeName, Double latitude, Double longitude) {}

  public static AdminRoundResDto of(
      ZoneEventRound round, List<ZoneEventRoundSlot> slots, List<ZoneEventBackupTarget> backups) {
    return new AdminRoundResDto(
        round.getId().toString(),
        round.getRoundType(),
        round.getStatus(),
        round.getStartsAt(),
        round.getEndsAt(),
        round.getTimezone(),
        round.getSettledAt(),
        slots.stream()
            .map(
                s ->
                    new Slot(
                        s.getId().toString(),
                        s.getSlotKind().name(),
                        s.getZoneId(),
                        s.getEventId() == null ? null : s.getEventId().toString()))
            .toList(),
        backups.stream()
            .map(
                b ->
                    new Backup(
                        b.getId().toString(), b.getPlaceName(), b.getLatitude(), b.getLongitude()))
            .toList());
  }
}
