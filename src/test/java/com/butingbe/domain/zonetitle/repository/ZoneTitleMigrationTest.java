package com.butingbe.domain.zonetitle.repository;

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

/** V36가 18개 칭호 정의를 시드하고 대표 칭호 부분 UK를 만드는지 검증한다. */
class ZoneTitleMigrationTest {

  @Test
  @DisplayName("칭호 정의 18개 시드와 유저당 대표 칭호 하나 제약")
  void migrateTitles() throws Exception {
    try (PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("buting_title_test")
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
        assertThat(count(connection, "zone_title_def")).isEqualTo(18);

        UUID userId = insertUser(connection);
        UUID def1 = defId(connection, "SUYEONG_NAMGU_T1");
        UUID def2 = defId(connection, "SUYEONG_NAMGU_T2");
        insertUserTitle(connection, userId, def1, "SUYEONG_NAMGU", true);
        // 두 번째 장착은 부분 UK 위반
        assertThat(
                catchThrowable(
                    () -> insertUserTitle(connection, userId, def2, "SUYEONG_NAMGU", true)))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("uk_user_zone_title_equipped");
      }
    }
  }

  private void insertUserTitle(
      Connection connection, UUID userId, UUID defId, String zoneId, boolean equipped)
      throws SQLException {
    try (PreparedStatement s =
        connection.prepareStatement(
            "INSERT INTO user_zone_title (user_title_id, user_id, title_def_id, zone_id, equipped, earned_at) "
                + "VALUES (gen_random_uuid(), ?, ?, ?, ?, now())")) {
      s.setObject(1, userId);
      s.setObject(2, defId);
      s.setString(3, zoneId);
      s.setBoolean(4, equipped);
      s.executeUpdate();
    }
  }

  private UUID defId(Connection connection, String code) throws SQLException {
    try (PreparedStatement s =
        connection.prepareStatement(
            "SELECT title_def_id FROM zone_title_def WHERE title_code = ?")) {
      s.setString(1, code);
      try (ResultSet rs = s.executeQuery()) {
        rs.next();
        return rs.getObject(1, UUID.class);
      }
    }
  }

  private UUID insertUser(Connection connection) throws SQLException {
    UUID userId = UUID.randomUUID();
    try (PreparedStatement s =
        connection.prepareStatement(
            "INSERT INTO users (id, email, last_name, first_name, nickname, role, created_at, updated_at) "
                + "VALUES (?, ?, '홍', '길동', 'tester', 'USER', now(), now())")) {
      s.setObject(1, userId);
      s.setString(2, "title-" + userId + "@example.com");
      s.executeUpdate();
    }
    return userId;
  }

  private long count(Connection connection, String table) throws SQLException {
    try (PreparedStatement s = connection.prepareStatement("SELECT count(*) FROM " + table)) {
      try (ResultSet rs = s.executeQuery()) {
        rs.next();
        return rs.getLong(1);
      }
    }
  }
}
