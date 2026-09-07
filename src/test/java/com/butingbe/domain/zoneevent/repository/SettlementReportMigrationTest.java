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

/** V40이 만드는 정산 리포트 PK/FK와 감사 로그를 검증한다. */
class SettlementReportMigrationTest {

  @Test
  @DisplayName("정산 리포트는 회차당 하나(PK)이고 없는 회차는 FK로 막힌다. 감사 로그는 추가된다.")
  void migrate() throws Exception {
    try (PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("buting_settle_test")
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
        UUID round = insertRound(c);
        insertReport(c, round);
        assertThat(catchThrowable(() -> insertReport(c, round)))
            .isInstanceOf(SQLException.class); // PK 중복
        assertThat(catchThrowable(() -> insertReport(c, UUID.randomUUID())))
            .isInstanceOf(SQLException.class); // FK 위반
        insertAudit(c, UUID.randomUUID());
      }
    }
  }

  private UUID insertRound(Connection c) throws SQLException {
    UUID id = UUID.randomUUID();
    try (PreparedStatement s =
        c.prepareStatement(
            "INSERT INTO zone_event_round (round_id, starts_at, ends_at, status) VALUES (?, now(), now(), 'CLOSED')")) {
      s.setObject(1, id);
      s.executeUpdate();
    }
    return id;
  }

  private void insertReport(Connection c, UUID round) throws SQLException {
    try (PreparedStatement s =
        c.prepareStatement(
            "INSERT INTO zone_event_settlement_report (round_id, report) VALUES (?, '{}'::jsonb)")) {
      s.setObject(1, round);
      s.executeUpdate();
    }
  }

  private void insertAudit(Connection c, UUID actor) throws SQLException {
    try (PreparedStatement s =
        c.prepareStatement(
            "INSERT INTO zone_event_audit_log (audit_id, actor_id, action, target_type) VALUES (gen_random_uuid(), ?, 'SETTLE_ROUND', 'ROUND')")) {
      s.setObject(1, actor);
      s.executeUpdate();
    }
  }
}
