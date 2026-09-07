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
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차를 시각에 맞춰 열고 닫으며, 슬롯에 연결된 이벤트를 함께 전환한다.
 *
 * <p>ShedLock 없이 DB 상태 기반 멱등으로 동작한다. SCHEDULED 회차만 OPEN으로, OPEN 회차만 CLOSED로 바꾸므로 여러 인스턴스가 동시에 돌아도
 * 상태 조건이 이미 소진돼 중복 전환이 일어나지 않는다. 이벤트 활성화도 SCHEDULED인 것만 대상으로 한다.
 *
 * <p>정산(TOP_LIKE, SETTLED 전환)은 이 스케줄러가 아니라 정산 잡(후속 이슈)이 담당한다. 여기서는 회차·이벤트의 열고 닫기까지만 한다.
 */
@Service
@RequiredArgsConstructor
public class ZoneEventRoundScheduler {

  private static final String SEOUL_ZONE = "Asia/Seoul";

  private final ZoneEventRoundRepository roundRepository;
  private final ZoneEventRoundSlotRepository slotRepository;
  private final ZoneEventRepository zoneEventRepository;

  @Scheduled(
      fixedDelayString = "${zone-event.round.scheduler.delay-ms:60000}",
      initialDelayString = "${zone-event.round.scheduler.initial-delay-ms:60000}")
  @Transactional
  public void advanceRounds() {
    advance(OffsetDateTime.now(java.time.ZoneId.of(SEOUL_ZONE)));
  }

  /** 주어진 시각 기준으로 회차를 열고 닫는다. 테스트에서 시각을 주입한다. */
  @Transactional
  public void advance(OffsetDateTime now) {
    for (ZoneEventRound round :
        roundRepository.findByStatusAndStartsAtLessThanEqual(RoundStatus.SCHEDULED, now)) {
      round.open();
      transitionSlotEvents(round, ZoneEventStatus.SCHEDULED, ZoneEventStatus.ACTIVE);
    }
    for (ZoneEventRound round :
        roundRepository.findByStatusAndEndsAtLessThanEqual(RoundStatus.OPEN, now)) {
      round.close();
      transitionSlotEvents(round, ZoneEventStatus.ACTIVE, ZoneEventStatus.CLOSED);
    }
  }

  private void transitionSlotEvents(
      ZoneEventRound round, ZoneEventStatus from, ZoneEventStatus to) {
    List<java.util.UUID> eventIds =
        slotRepository.findByRound_Id(round.getId()).stream()
            .map(ZoneEventRoundSlot::getEventId)
            .filter(id -> id != null)
            .toList();
    if (eventIds.isEmpty()) {
      return;
    }
    for (ZoneEvent event : zoneEventRepository.findAllById(eventIds)) {
      if (event.getStatus() != from) {
        continue; // 멱등: 이미 원하는 상태거나 다른 상태면 건너뛴다.
      }
      if (to == ZoneEventStatus.ACTIVE) {
        event.activate();
      } else if (to == ZoneEventStatus.CLOSED) {
        event.close();
      }
    }
  }
}
