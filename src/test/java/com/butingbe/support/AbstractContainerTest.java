package com.butingbe.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
// ❌ 기존 @Testcontainers 어노테이션은 확실하게 지워줍니다! (수동 제어를 위해)
public abstract class AbstractContainerTest {

  // 🐳 static 블록에서 제어하기 위해 @Container 어노테이션 제거
  private static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("buting_test")
          .withUsername("test_user")
          .withPassword("test_password");

  // 🌟 [핵심] 전체 테스트 세션(JVM) 전체에서 최초 딱 1번만 도커를 실행합니다.
  // 테스트 클래스가 바뀌어도 이 포트는 절대 변하지 않고 고정됩니다.
  static {
    postgres.start();
    createShedlockTable();
  }

  /**
   * ShedLock 잠금 테이블은 엔티티가 아니라 Flyway 마이그레이션(V59)으로 만들어진다. 이 테스트들은 Flyway 를 끄고 {@code ddl-auto:
   * create-drop} 으로 도는 탓에 그 테이블이 생기지 않는다. 컨테이너를 띄운 직후 한 번 만들어 둔다.
   *
   * <p>테스트를 Flyway + validate 로 돌리면 이 보정이 통째로 필요 없어진다. #327 참고.
   */
  private static void createShedlockTable() {
    try (java.sql.Connection connection =
            java.sql.DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        java.sql.Statement statement = connection.createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS shedlock (
              name       VARCHAR(64)  NOT NULL,
              lock_until TIMESTAMP    NOT NULL,
              locked_at  TIMESTAMP    NOT NULL,
              locked_by  VARCHAR(255) NOT NULL,
              CONSTRAINT shedlock_pkey PRIMARY KEY (name)
          )
          """);
    } catch (java.sql.SQLException e) {
      throw new IllegalStateException("ShedLock 테스트 테이블을 만들지 못했다.", e);
    }
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    registry.add("admin.token", () -> "test-admin-token");
  }
}
