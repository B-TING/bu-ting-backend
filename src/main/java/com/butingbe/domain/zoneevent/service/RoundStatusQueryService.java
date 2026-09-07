package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.domain.zoneevent.dto.response.RoundStatusResDto;
import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유저 회차 현황(FR-EVT-03). 지금 열린 회차가 있으면 6구역을 OPEN/REST로, 없으면 다음 예정 회차의 구역을 UPCOMING으로 보여준다. 열린 회차도 다음
 * 회차도 없으면 404다.
 */
@Service
@RequiredArgsConstructor
public class RoundStatusQueryService {

  private final ZoneEventRoundRepository roundRepository;
  private final ZoneEventRoundSlotRepository slotRepository;

  @Transactional(readOnly = true)
  public RoundStatusResDto current() {
    return roundRepository
        .findFirstByStatusOrderByStartsAtDesc(RoundStatus.OPEN)
        .map(round -> statusOf(round, "OPEN"))
        .or(
            () ->
                roundRepository
                    .findFirstByStatusAndStartsAtGreaterThanEqualOrderByStartsAtAsc(
                        RoundStatus.SCHEDULED, OffsetDateTime.now(ZoneId.of("Asia/Seoul")))
                    .map(round -> statusOf(round, "UPCOMING")))
        .orElseThrow(() -> new ResourceNotFoundException("error.zone_event.not_found"));
  }

  private RoundStatusResDto statusOf(ZoneEventRound round, String openLabel) {
    Map<String, ZoneEventRoundSlot> slotsByZone =
        slotRepository.findByRound_Id(round.getId()).stream()
            .collect(
                Collectors.toMap(ZoneEventRoundSlot::getZoneId, Function.identity(), (a, b) -> a));
    List<RoundStatusResDto.ZoneSlot> zones =
        java.util.Arrays.stream(ChatZone.values())
            .map(
                zone -> {
                  ZoneEventRoundSlot slot = slotsByZone.get(zone.name());
                  if (slot == null) {
                    return new RoundStatusResDto.ZoneSlot(zone.name(), "REST", null);
                  }
                  UUID eventId = slot.getEventId();
                  return new RoundStatusResDto.ZoneSlot(
                      zone.name(), openLabel, eventId == null ? null : eventId.toString());
                })
            .toList();
    return new RoundStatusResDto(
        round.getId().toString(), round.getStatus(), round.getStartsAt(), round.getEndsAt(), zones);
  }
}
