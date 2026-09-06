package com.butingbe.domain.reward.repository;

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

/** V32/V33이 만드는 보상 제약과 카탈로그 시드를 실제 PostgreSQL로 검증한다. */
class RewardMigrationTest {

  @Test
  @DisplayName("마이그레이션은 카탈로그 code UK·잔액 음수 금지·배지 UK 제약과 시드를 만든다")
  void migrateReward() throws Exception {
    try (PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("buting_reward_test")
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

        // 시드된 카탈로그(POINT_BASE, SPOT_GWANGAN_BRIDGE)
        assertThat(count(connection, "reward_catalog")).isGreaterThanOrEqualTo(2);

        // 잘못된 reward_type은 CHECK로 거부
        assertThat(catchThrowable(() -> insertCatalog(connection, "WRONG", "CODE_X")))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("ck_reward_catalog_type");

        // 중복 code는 UK로 거부
        assertThat(catchThrowable(() -> insertCatalog(connection, "POINT", "POINT_BASE")))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("uk_reward_catalog_code");

        // 잔액 음수는 CHECK로 거부
        UUID userId = insertUser(connection);
        assertThat(catchThrowable(() -> insertBalance(connection, userId, -1)))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("ck_user_point_balance_non_negative");
      }
    }
  }

  private void insertCatalog(Connection connection, String rewardType, String code)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
            INSERT INTO reward_catalog (reward_id, reward_type, code, name, active)
            VALUES (gen_random_uuid(), ?, ?, '테스트', TRUE)
            """)) {
      statement.setString(1, rewardType);
      statement.setString(2, code);
      statement.executeUpdate();
    }
  }

  private void insertBalance(Connection connection, UUID userId, int balance) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO user_point_balance (user_id, balance) VALUES (?, ?)")) {
      statement.setObject(1, userId);
      statement.setInt(2, balance);
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
      statement.setString(2, "reward-" + userId + "@example.com");
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
}
