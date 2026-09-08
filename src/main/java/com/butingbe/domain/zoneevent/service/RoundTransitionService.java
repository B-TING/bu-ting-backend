package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.zoneevent.entity.RoundStatus;
import com.butingbe.domain.zoneevent.entity.ZoneEvent;
import com.butingbe.domain.zoneevent.entity.ZoneEventRound;
import com.butingbe.domain.zoneevent.entity.ZoneEventRoundSlot;
import com.butingbe.domain.zoneevent.entity.ZoneEventStatus;
import com.butingbe.domain.zoneevent.repository.ZoneEventRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundRepository;
import com.butingbe.domain.zoneevent.repository.ZoneEventRoundSlotRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차·슬롯 이벤트의 서버 시간 기준 자동 전환을 한 곳에서 처리한다.
 *
 * <p>스케줄러(주기 실행)와 요청 처리 경로(조회 직전 동기화) 양쪽이 이 서비스를 호출해, Job이 지연돼도 응답 시점엔 항상 최신 상태를 보장한다(멱등).
 */
@Service
@RequiredArgsConstructor
public class RoundTransitionService {

  private final ZoneEventRoundRepository roundRepository;
  private final ZoneEventRoundSlotRepository slotRepository;
  private final ZoneEventRepository zoneEventRepository;

  /**
   * 주어진 시각 기준으로 이 회차 하나를 필요하면 전환한다. 조건이 안 맞으면 아무 것도 하지 않는다.
   *
   * <p>두 조건을 독립된 {@code if}로 검사하므로, 시작·종료 시각이 모두 지난 SCHEDULED 회차는 한 번의 호출로 ACTIVE를 거쳐 CLOSED까지 이어서
   * 전환된다(조회 경로처럼 sync가 한 번만 불리는 곳에서도 최종 상태가 보장된다).
   */
  @Transactional
  public void sync(ZoneEventRound round, OffsetDateTime now) {
    if (round.getStatus() == RoundStatus.SCHEDULED && !round.getStartsAt().isAfter(now)) {
      round.activate();
      transitionSlotEvents(round, ZoneEventStatus.SCHEDULED, ZoneEventStatus.ACTIVE);
    }
    if (round.getStatus() == RoundStatus.ACTIVE && !round.getEndsAt().isAfter(now)) {
      round.close();
      transitionSlotEvents(round, ZoneEventStatus.ACTIVE, ZoneEventStatus.CLOSED);
    }
  }

  /** 전체 회차를 훑어 대상이 되는 것만 전환한다(스케줄러 진입점). */
  @Transactional
  public void syncAll(OffsetDateTime now) {
    for (ZoneEventRound round :
        roundRepository.findByStatusAndStartsAtLessThanEqual(RoundStatus.SCHEDULED, now)) {
      sync(round, now);
    }
    for (ZoneEventRound round :
        roundRepository.findByStatusAndEndsAtLessThanEqual(RoundStatus.ACTIVE, now)) {
      sync(round, now);
    }
  }

  private void transitionSlotEvents(
      ZoneEventRound round, ZoneEventStatus from, ZoneEventStatus to) {
    List<UUID> eventIds =
        slotRepository.findByRound_Id(round.getId()).stream()
            .map(ZoneEventRoundSlot::getEventId)
            .filter(id -> id != null)
            .toList();
    if (eventIds.isEmpty()) {
      return;
    }
    for (ZoneEvent event : zoneEventRepository.findAllById(eventIds)) {
      if (event.getStatus() != from) {
        continue;
      }
      if (to == ZoneEventStatus.ACTIVE) {
        event.activate();
      } else {
        event.close();
      }
    }
  }
}
