package com.butingbe.global.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 스케줄러 분산 잠금.
 *
 * <p>지금은 인스턴스가 하나라 중복 실행이 없지만, 스케일아웃하는 순간 같은 작업이 동시에 돈다. 라운드 정산은 그때 보상이 두 번 나간다. 인스턴스를 늘리기 전에 넣어 두는
 * 안전장치다.
 *
 * <p>{@code defaultLockAtMostFor} 는 잠금을 쥔 인스턴스가 죽었을 때 잠금이 풀리는 상한이다. 이 시간이 지나면 다른 인스턴스가 가져간다.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerLockConfig {

  @Bean
  public LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .usingDbTime()
            .build());
  }
}
