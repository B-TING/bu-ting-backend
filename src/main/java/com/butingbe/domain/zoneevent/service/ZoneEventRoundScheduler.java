package com.butingbe.domain.zoneevent.service;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차를 시각에 맞춰 열고 닫는 주기 작업. 실제 전환 로직은 {@link RoundTransitionService}에 있고, 여기서는 주기 실행만 담당한다.
 *
 * <p>정산(TOP_LIKE, SETTLED 전환)은 이 스케줄러가 아니라 정산 잡(후속 이슈)이 담당한다.
 */
@Service
@RequiredArgsConstructor
public class ZoneEventRoundScheduler {

  private static final String SEOUL_ZONE = "Asia/Seoul";

  private final RoundTransitionService transitionService;

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
    transitionService.syncAll(now);
  }
}
