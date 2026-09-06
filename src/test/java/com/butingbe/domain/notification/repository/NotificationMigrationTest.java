package com.butingbe.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/** V39가 만드는 토큰 UK·플랫폼 CHECK·구독 UK를 검증한다. */
class NotificationMigrationTest {

  @Test
  @DisplayName("토큰 유일·플랫폼 CHECK·구역 구독 유일 제약")
  void migrate() throws Exception {
    try (PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("buting_notif_test")
            .withUsername("test_user")
            .withPassword("test_password")) {
      postgres.start();
      Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .locations("classpath:db/migration")
          .load()
          .migrate();
      try (Connection c =
          DriverManager.getConnection(
              postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
        UUID user = insertUser(c);
        insertToken(c, user, "tok-1", "IOS");
        assertThat(catchThrowable(() -> insertToken(c, user, "tok-1", "ANDROID")))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("uk_user_device_token");
        assertThat(catchThrowable(() -> insertToken(c, user, "tok-2", "WINDOWS")))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("ck_user_device_token_platform");
        insertSub(c, user, "SUYEONG_NAMGU");
        assertThat(catchThrowable(() -> insertSub(c, user, "SUYEONG_NAMGU")))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("uk_user_zone_subscription");
      }
    }
  }

  private void insertToken(Connection c, UUID user, String token, String platform)
      throws SQLException {
    try (PreparedStatement s =
        c.prepareStatement(
            "INSERT INTO user_device_token (token_id, user_id, fcm_token, platform) VALUES (gen_random_uuid(), ?, ?, ?)")) {
      s.setObject(1, user);
      s.setString(2, token);
      s.setString(3, platform);
      s.executeUpdate();
    }
  }

  private void insertSub(Connection c, UUID user, String zone) throws SQLException {
    try (PreparedStatement s =
        c.prepareStatement(
            "INSERT INTO user_zone_subscription (subscription_id, user_id, zone_id) VALUES (gen_random_uuid(), ?, ?)")) {
      s.setObject(1, user);
      s.setString(2, zone);
      s.executeUpdate();
    }
  }

  private UUID insertUser(Connection c) throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement s =
        c.prepareStatement(
            "INSERT INTO users (id, email, last_name, first_name, nickname, role, created_at, updated_at) VALUES (?, ?, '홍', '길동', 'tester', 'USER', now(), now())")) {
      s.setObject(1, id);
      s.setString(2, "notif-" + id + "@example.com");
      s.executeUpdate();
    }
    return id;
  }
}
