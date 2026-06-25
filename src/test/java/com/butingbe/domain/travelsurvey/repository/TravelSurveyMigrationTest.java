package com.butingbe.domain.travelsurvey.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class TravelSurveyMigrationTest {

  @Test
  @DisplayName("Flyway 마이그레이션은 travel_survey CHECK 제약조건과 GIN 인덱스를 생성한다")
  void migrateTravelSurveySchema() throws Exception {
    try (PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("buting_migration_test")
            .withUsername("test_user")
            .withPassword("test_password")) {
      postgres.start();

      Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .locations("classpath:db/migration")
          .load()
          .migrate();

      try (java.sql.Connection connection =
          DriverManager.getConnection(
              postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
        assertThat(hasGinIndex(connection, "idx_travel_survey_purposes")).isTrue();
        assertThat(hasCheckConstraint(connection, "chk_skipped_all_consistency")).isTrue();
        assertThat(invalidSkippedAllInsert(connection))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("chk_skipped_all_consistency");
      }
    }
  }

  private boolean hasGinIndex(java.sql.Connection connection, String indexName)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'travel_survey'
                  AND indexname = ?
                """)) {
      statement.setString(1, indexName);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next() && resultSet.getString("indexdef").contains("USING gin");
      }
    }
  }

  private boolean hasCheckConstraint(java.sql.Connection connection, String constraintName)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                SELECT 1
                FROM pg_constraint
                WHERE conname = ?
                  AND contype = 'c'
                """)) {
      statement.setString(1, constraintName);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }

  private Throwable invalidSkippedAllInsert(java.sql.Connection connection) throws SQLException {
    UUID userId = UUID.randomUUID();
    try (PreparedStatement insertUser =
        connection.prepareStatement(
            """
            INSERT INTO users (
              id, email, last_name, first_name, nickname, role, created_at, updated_at
            )
            VALUES (?, ?, '홍', '길동', 'tester', 'USER', now(), now())
            """)) {
      insertUser.setObject(1, userId);
      insertUser.setString(2, "migration-survey@example.com");
      insertUser.executeUpdate();
    }

    return org.assertj.core.api.Assertions.catchThrowable(
        () -> {
          try (PreparedStatement insertSurvey =
              connection.prepareStatement(
                  """
                  INSERT INTO travel_survey (
                    user_id, preferred_language, is_planned, skipped_all, created_at, updated_at
                  )
                  VALUES (?, 'ko', true, true, now(), now())
                  """)) {
            insertSurvey.setObject(1, userId);
            insertSurvey.executeUpdate();
          }
        });
  }
}
