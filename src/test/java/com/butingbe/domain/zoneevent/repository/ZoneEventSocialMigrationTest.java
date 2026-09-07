package com.butingbe.domain.zoneevent.repository;

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

/** V35가 만드는 좋아요·신고 UK/CHECK 제약을 실제 PostgreSQL로 검증한다. */
class ZoneEventSocialMigrationTest {

  @Test
  @DisplayName("마이그레이션은 좋아요·신고 유니크와 사유·상태 제약을 만든다")
  void migrateSocial() throws Exception {
    try (PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("buting_social_test")
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
        UUID userId = insertUser(connection);
        UUID participationId = insertParticipation(connection, userId);

        insertLike(connection, participationId, userId);
        assertThat(catchThrowable(() -> insertLike(connection, participationId, userId)))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("uk_zone_event_like_participation_user");

        insertReport(connection, participationId, userId, "SPAM");
        assertThat(catchThrowable(() -> insertReport(connection, participationId, userId, "SPAM")))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("uk_zone_event_report_participation_reporter");

        UUID other = insertUser(connection);
        assertThat(catchThrowable(() -> insertReport(connection, participationId, other, "WRONG")))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("ck_zone_event_report_reason");
      }
    }
  }

  private void insertLike(Connection connection, UUID participationId, UUID userId)
      throws SQLException {
    try (PreparedStatement s =
        connection.prepareStatement(
            "INSERT INTO zone_event_like (like_id, participation_id, user_id) VALUES (gen_random_uuid(), ?, ?)")) {
      s.setObject(1, participationId);
      s.setObject(2, userId);
      s.executeUpdate();
    }
  }

  private void insertReport(
      Connection connection, UUID participationId, UUID reporterId, String reason)
      throws SQLException {
    try (PreparedStatement s =
        connection.prepareStatement(
            "INSERT INTO zone_event_report (report_id, participation_id, reporter_id, reason_code) "
                + "VALUES (gen_random_uuid(), ?, ?, ?)")) {
      s.setObject(1, participationId);
      s.setObject(2, reporterId);
      s.setString(3, reason);
      s.executeUpdate();
    }
  }

  private UUID insertParticipation(Connection connection, UUID userId) throws SQLException {
    UUID typeCode = null;
    try (PreparedStatement t =
        connection.prepareStatement(
            "INSERT INTO zone_event_type (type_code, name, requires_upload) VALUES ('T', 'T', true) ON CONFLICT DO NOTHING")) {
      t.executeUpdate();
    }
    UUID eventId = UUID.randomUUID();
    try (PreparedStatement e =
        connection.prepareStatement(
            "INSERT INTO zone_event (event_id, zone_id, type_code, title, starts_at, duration_minutes, status, base_reward) "
                + "VALUES (?, 'SUYEONG_NAMGU', 'T', 't', now(), 60, 'ACTIVE', '{}'::jsonb)")) {
      e.setObject(1, eventId);
      e.executeUpdate();
    }
    UUID participationId = UUID.randomUUID();
    try (PreparedStatement p =
        connection.prepareStatement(
            "INSERT INTO zone_event_participation (participation_id, event_id, user_id, status, gps_lat, gps_lng, joined_at) "
                + "VALUES (?, ?, ?, 'SUCCESS', 35.1, 129.1, now())")) {
      p.setObject(1, participationId);
      p.setObject(2, eventId);
      p.setObject(3, userId);
      p.executeUpdate();
    }
    return participationId;
  }

  private UUID insertUser(Connection connection) throws SQLException {
    UUID userId = UUID.randomUUID();
    try (PreparedStatement s =
        connection.prepareStatement(
            "INSERT INTO users (id, email, last_name, first_name, nickname, role, created_at, updated_at) "
                + "VALUES (?, ?, '홍', '길동', 'tester', 'USER', now(), now())")) {
      s.setObject(1, userId);
      s.setString(2, "social-" + userId + "@example.com");
      s.executeUpdate();
    }
    return userId;
  }
}
