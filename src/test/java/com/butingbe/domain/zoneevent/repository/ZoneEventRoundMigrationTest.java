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

/** V34가 만드는 회차·슬롯 제약을 실제 PostgreSQL로 검증한다. */
class ZoneEventRoundMigrationTest {

  @Test
  @DisplayName("마이그레이션은 회차 상태·슬롯 UK·예비 타겟 반경 제약을 만든다")
  void migrateRound() throws Exception {
    try (PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("buting_round_test")
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
        assertThat(hasCheckConstraint(connection, "ck_zone_event_round_status")).isTrue();

        UUID roundId = insertRound(connection, "OPEN");
        assertThat(catchThrowable(() -> insertRound(connection, "WRONG")))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("ck_zone_event_round_status");

        // 회차·구역당 슬롯 하나 — 같은 구역 두 번째는 UK 위반
        insertSlot(connection, roundId, "SUYEONG_NAMGU");
        assertThat(catchThrowable(() -> insertSlot(connection, roundId, "SUYEONG_NAMGU")))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("uk_zone_event_round_slot");

        // 예비 타겟 반경 범위 밖 거부
        assertThat(catchThrowable(() -> insertBackup(connection, roundId, 10)))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("ck_zone_event_backup_target_radius");
      }
    }
  }

  private UUID insertRound(Connection connection, String status) throws SQLException {
    UUID roundId = UUID.randomUUID();
    try (PreparedStatement s =
        connection.prepareStatement(
            """
            INSERT INTO zone_event_round (round_id, starts_at, ends_at, status)
            VALUES (?, now(), now() + interval '1 day', ?)
            """)) {
      s.setObject(1, roundId);
      s.setString(2, status);
      s.executeUpdate();
    }
    return roundId;
  }

  private void insertSlot(Connection connection, UUID roundId, String zoneId) throws SQLException {
    try (PreparedStatement s =
        connection.prepareStatement(
            """
            INSERT INTO zone_event_round_slot (slot_id, round_id, slot_kind, zone_id)
            VALUES (gen_random_uuid(), ?, 'AUTH', ?)
            """)) {
      s.setObject(1, roundId);
      s.setString(2, zoneId);
      s.executeUpdate();
    }
  }

  private void insertBackup(Connection connection, UUID roundId, int radiusM) throws SQLException {
    try (PreparedStatement s =
        connection.prepareStatement(
            """
            INSERT INTO zone_event_backup_target (
              target_id, round_id, target_kind, place_name, latitude, longitude, radius_m)
            VALUES (gen_random_uuid(), ?, 'PLACE', '예비', 35.1, 129.1, ?)
            """)) {
      s.setObject(1, roundId);
      s.setInt(2, radiusM);
      s.executeUpdate();
    }
  }

  private boolean hasCheckConstraint(Connection connection, String name) throws SQLException {
    try (PreparedStatement s =
        connection.prepareStatement(
            "SELECT 1 FROM pg_constraint WHERE conname = ? AND contype = 'c'")) {
      s.setString(1, name);
      try (ResultSet rs = s.executeQuery()) {
        return rs.next();
      }
    }
  }
}
