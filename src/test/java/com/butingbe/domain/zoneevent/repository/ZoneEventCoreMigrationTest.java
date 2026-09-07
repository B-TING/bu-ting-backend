package com.butingbe.domain.zoneevent.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/** V30/V31 마이그레이션이 만드는 구역 이벤트 제약과 시드를 실제 PostgreSQL로 검증한다. */
class ZoneEventCoreMigrationTest {

  @Test
  @DisplayName("마이그레이션은 구역·반경·열린참여 제약과 이벤트 타입 시드를 만든다")
  void migrateZoneEventCore() throws Exception {
    try (PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("buting_zone_event_test")
            .withUsername("test_user")
            .withPassword("test_password")) {
      postgres.start();

      Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .locations("classpath:db/migration")
          .load()
          .migrate();

      try (Connection connection =
          DriverManager.getConnection(
              postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {

        // 시드된 이벤트 타입
        assertThat(count(connection, "zone_event_type")).isGreaterThanOrEqualTo(2);
        assertThat(hasCheckConstraint(connection, "ck_zone_event_zone_id")).isTrue();
        assertThat(hasCheckConstraint(connection, "ck_zone_event_auth_target_radius")).isTrue();

        // 6구역 외 zone_id는 CHECK로 거부
        assertThat(catchThrowable(() -> insertEvent(connection, "NOWHERE", 100)))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("ck_zone_event_zone_id");

        // 유효한 구역 + 반경 밖 타겟은 radius CHECK로 거부
        UUID eventId = insertEvent(connection, "SUYEONG_NAMGU", 1440);
        assertThat(catchThrowable(() -> insertAuthTarget(connection, eventId, 10)))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("ck_zone_event_auth_target_radius");

        // 열린 참여는 유저·이벤트당 하나 — 두 번째 JOINED는 부분 UK로 거부
        UUID userId = insertUser(connection);
        insertParticipation(connection, eventId, userId, "JOINED");
        assertThat(catchThrowable(() -> insertParticipation(connection, eventId, userId, "JOINED")))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("uk_zone_event_participation_open");
      }
    }
  }

  private UUID insertEvent(Connection connection, String zoneId, int durationMinutes)
      throws SQLException {
    UUID eventId = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
            INSERT INTO zone_event (
              event_id, zone_id, type_code, title, starts_at, duration_minutes, status, base_reward)
            VALUES (?, ?, 'PLACE_AUTH', '테스트', now(), ?, 'ACTIVE', '{"points":50}'::jsonb)
            """)) {
      statement.setObject(1, eventId);
      statement.setString(2, zoneId);
      statement.setInt(3, durationMinutes);
      statement.executeUpdate();
    }
    return eventId;
  }

  private void insertAuthTarget(Connection connection, UUID eventId, int radiusM)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
            INSERT INTO zone_event_auth_target (
              target_id, event_id, target_kind, place_name, latitude, longitude, radius_m)
            VALUES (gen_random_uuid(), ?, 'PLACE', '광안대교', 35.153, 129.118, ?)
            """)) {
      statement.setObject(1, eventId);
      statement.setInt(2, radiusM);
      statement.executeUpdate();
    }
  }

  private void insertParticipation(Connection connection, UUID eventId, UUID userId, String status)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
            INSERT INTO zone_event_participation (
              participation_id, event_id, user_id, status, gps_lat, gps_lng, joined_at)
            VALUES (gen_random_uuid(), ?, ?, ?, 35.153, 129.118, now())
            """)) {
      statement.setObject(1, eventId);
      statement.setObject(2, userId);
      statement.setString(3, status);
      statement.executeUpdate();
    }
  }

  private UUID insertUser(Connection connection) throws SQLException {
    UUID userId = UUID.randomUUID();
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
            INSERT INTO users (
              id, email, last_name, first_name, nickname, role, created_at, updated_at)
            VALUES (?, ?, '홍', '길동', 'tester', 'USER', now(), now())
            """)) {
      statement.setObject(1, userId);
      statement.setString(2, "zone-event-" + userId + "@example.com");
      statement.executeUpdate();
    }
    return userId;
  }

  private long count(Connection connection, String table) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT count(*) FROM " + table)) {
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getLong(1);
      }
    }
  }

  private boolean hasCheckConstraint(Connection connection, String constraintName)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT 1 FROM pg_constraint WHERE conname = ? AND contype = 'c'")) {
      statement.setString(1, constraintName);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }
}
