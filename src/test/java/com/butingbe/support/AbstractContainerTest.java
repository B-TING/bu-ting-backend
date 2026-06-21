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
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
  }
}
