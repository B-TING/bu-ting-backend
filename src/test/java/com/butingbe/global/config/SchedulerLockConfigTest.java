package com.butingbe.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.route.RouteCacheEvictionScheduler;
import com.butingbe.support.AbstractContainerTest;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 스케줄러 잠금이 실제로 걸리는지 본다.
 *
 * <p>의존성을 넣고 애너테이션을 달아도, 프록시가 안 걸리면 아무 일도 일어나지 않는다. 그런데 테스트는 그대로 통과한다 -- 그냥 잠금 없이 도는 것이기 때문이다. 그래서
 * "예외 없이 실행된다"가 아니라 잠금 기록이 남는지를 확인한다.
 */
class SchedulerLockConfigTest extends AbstractContainerTest {

  private static final String LOCK_NAME = "routeCacheEviction";

  @Autowired private RouteCacheEvictionScheduler scheduler;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("스케줄 메서드를 부르면 잠금 기록이 남고, 잠금은 미래까지 유지된다")
  void scheduledMethodTakesALock() {
    jdbcTemplate.update("DELETE FROM shedlock WHERE name = ?", LOCK_NAME);

    scheduler.evictExpiredDaily();

    assertThat(lockCount()).describedAs("잠금 행이 생겨야 한다. 없으면 프록시가 걸리지 않은 것이다.").isEqualTo(1);
    // lockAtLeastFor 만큼은 잠금을 쥐고 있어야 다른 인스턴스가 곧바로 같은 일을 시작하지 않는다.
    assertThat(lockUntil()).isAfter(Instant.now());
  }

  private int lockCount() {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM shedlock WHERE name = ?", Integer.class, LOCK_NAME);
    return count == null ? 0 : count;
  }

  private Instant lockUntil() {
    return jdbcTemplate.query(
        "SELECT lock_until FROM shedlock WHERE name = ?",
        (ResultSet rs) -> {
          assertThat(rs.next()).isTrue();
          // ShedLock 은 lock_until 을 timezone 없는 컬럼에 UTC 로 쓴다. 달력을 주지 않으면
          // JVM 기본 시간대(Asia/Seoul)로 해석돼 9시간 어긋난다.
          return rs.getTimestamp(1, Calendar.getInstance(TimeZone.getTimeZone("UTC"))).toInstant();
        },
        LOCK_NAME);
  }
}
