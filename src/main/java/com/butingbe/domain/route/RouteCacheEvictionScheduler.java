package com.butingbe.domain.route;

import com.butingbe.domain.route.repository.PlaceTravelTimeRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만료된 경로 캐시를 주기적으로 지운다.
 *
 * <p>조회 시 미스로 취급하는 것만으로는 행이 계속 쌓인다. 외부 지도 데이터는 보관 기간 제약이 있어 방치할 수 없다.
 *
 * <p>중복 실행을 막는 장치를 두지 않는다. 같은 조건의 삭제라 여러 인스턴스가 동시에 돌아도 결과가 같다.
 */
@Service
@Slf4j
@ConditionalOnProperty(
    name = "route.cache.eviction.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RouteCacheEvictionScheduler {

  private static final String SEOUL_ZONE = "Asia/Seoul";

  private final PlaceTravelTimeRepository repository;
  private final Duration ttl;
  private final Clock clock = Clock.system(java.time.ZoneId.of(SEOUL_ZONE));

  public RouteCacheEvictionScheduler(
      PlaceTravelTimeRepository repository, @Value("${route.cache.ttl-days:30}") long ttlDays) {
    this.repository = repository;
    this.ttl = Duration.ofDays(ttlDays);
  }

  @Scheduled(cron = "${route.cache.eviction.cron:0 30 4 * * *}", zone = SEOUL_ZONE)
  @SchedulerLock(name = "routeCacheEviction", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
  public void evictExpiredDaily() {
    evictExpired(LocalDateTime.now(clock));
  }

  /** 주어진 시각 기준으로 만료된 행을 지운다. 테스트에서 시각을 주입한다. */
  @Transactional
  public void evictExpired(LocalDateTime now) {
    try {
      int deleted = repository.deleteFetchedBefore(now.minus(ttl));
      if (deleted > 0) {
        log.info("Evicted {} expired route cache entries.", deleted);
      }
    } catch (RuntimeException e) {
      // 캐시 정리가 실패해도 서비스는 계속 돌아야 한다. 다음 주기에 다시 시도된다.
      log.warn("Route cache eviction failed. reason={}", e.toString());
    }
  }
}
