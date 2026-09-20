package com.butingbe.domain.auth.service;

import com.butingbe.domain.auth.repository.OpaqueTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만료되거나 폐기된 지 오래된 토큰 행을 주기적으로 지운다.
 *
 * <p>인증은 만료·폐기 토큰을 무시할 뿐 지우지 않고, 회전이 폐기 이력을 남기므로 방치하면 {@code opaque_tokens}가 계속 커진다.
 *
 * <p>보관 기간을 두는 이유는 재사용 감지 때문이다. 폐기 직후 바로 지우면 탈취된 토큰이 다시 와도 "모르는 토큰"으로만 보인다.
 *
 * <p>중복 실행을 막는 장치를 두지 않는다. 같은 조건의 삭제라 여러 인스턴스가 동시에 돌아도 결과가 같다.
 */
@Service
@Slf4j
@ConditionalOnProperty(
    name = "auth.token.cleanup.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OpaqueTokenCleanupScheduler {

  private static final String SEOUL_ZONE = "Asia/Seoul";

  private final OpaqueTokenRepository repository;
  private final Duration retention;
  private final Clock clock = Clock.system(ZoneId.of(SEOUL_ZONE));

  public OpaqueTokenCleanupScheduler(
      OpaqueTokenRepository repository,
      @Value("${auth.token.cleanup.retention-days:7}") long retentionDays) {
    this.repository = repository;
    this.retention = Duration.ofDays(retentionDays);
  }

  @Scheduled(cron = "${auth.token.cleanup.cron:0 0 4 * * *}", zone = SEOUL_ZONE)
  @SchedulerLock(name = "opaqueTokenCleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
  public void cleanUpDaily() {
    cleanUp(LocalDateTime.now(clock));
  }

  /** 주어진 시각 기준으로 보관 기간이 지난 행을 지운다. 테스트에서 시각을 주입한다. */
  @Transactional
  public void cleanUp(LocalDateTime now) {
    try {
      int deleted = repository.deleteExpiredOrRevokedBefore(now.minus(retention));
      if (deleted > 0) {
        log.info("Deleted {} expired or revoked opaque tokens.", deleted);
      }
    } catch (RuntimeException e) {
      // 정리가 실패해도 인증은 계속 돌아야 한다. 다음 주기에 다시 시도된다.
      log.warn("Opaque token cleanup failed. reason={}", e.toString());
    }
  }
}
