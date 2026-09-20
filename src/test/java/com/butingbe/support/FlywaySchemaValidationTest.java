package com.butingbe.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 나머지 통합 테스트는 ddl-auto=create-drop 으로 엔티티에서 스키마를 만든다. 그 스키마는 Flyway 가 만드는 운영 스키마와 다를 수 있고, 어긋나도
 * 테스트는 통과한 뒤 운영 기동의 validate 에서 처음 터진다. 이 테스트만 운영과 같은 조합(Flyway 로 전체 마이그레이션 적용 +
 * ddl-auto=validate)으로 애플리케이션 컨텍스트를 띄워, 그 어긋남을 CI 에서 먼저 잡는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class FlywaySchemaValidationTest {

  private static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("buting_schema_validation")
          .withUsername("test_user")
          .withPassword("test_password");

  static {
    postgres.start();
  }

  @Autowired private Flyway flyway;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add("admin.token", () -> "test-admin-token");
  }

  @Test
  @DisplayName("전체 마이그레이션을 적용한 스키마를 엔티티 매핑이 validate 로 통과한다")
  void entitiesMatchMigratedSchema() {
    // 컨텍스트가 떴다는 것 자체가 validate 통과다. Flyway 가 실제로 돌았는지도 같이 확인해,
    // 설정이 꺼져 스키마가 비어 있는 채로 통과하는 상황을 막는다.
    assertThat(flyway.info().applied()).isNotEmpty();
  }
}
