package com.butingbe.domain.travelrecord.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 피드 검색용 trgm 인덱스가 실제로 쓰일 수 있는지 본다.
 *
 * <p>인덱스가 만들어졌다는 것만으로는 부족하다. 식 인덱스는 쿼리가 쓰는 식과 글자 그대로 같아야 쓰인다. {@code lower(coalesce(content, ''))}
 * 로 만들어 두고 쿼리가 {@code lower(content)} 를 쓰면 인덱스는 그냥 놀고, 아무도 모른다.
 *
 * <p>그래서 이름만 확인하지 않고 실행 계획을 본다. 행이 적으면 Postgres 가 어차피 순차 스캔을 고르므로, 순차 스캔을 끄고 "이 쿼리에 이 인덱스를 쓸 수
 * 있는가"를 묻는다.
 */
class TravelRecordSearchIndexMigrationTest {

  private static final String[] EXPECTED_INDEXES = {
    "idx_travel_record_title_trgm",
    "idx_travel_record_content_trgm",
    "idx_travel_record_place_name_trgm",
    "idx_travel_record_place_address_trgm"
  };

  @Test
  @DisplayName("피드 검색 식과 같은 형태의 조회가 trgm 인덱스를 탄다")
  void searchQueriesCanUseTrgmIndexes() throws Exception {
    try (PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("buting_search_index_test")
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
        for (String indexName : EXPECTED_INDEXES) {
          assertThat(indexExists(connection, indexName))
              .describedAs("인덱스 %s 가 만들어져야 한다", indexName)
              .isTrue();
        }

        try (Statement statement = connection.createStatement()) {
          statement.execute("SET enable_seqscan = off");
        }

        // TravelRecordRepository 의 피드 쿼리가 쓰는 식과 같은 형태다. 식이 어긋나면 계획에서 인덱스가 사라진다.
        assertThat(
                planOf(connection, "select id from travel_record where lower(title) like '%부산%'"))
            .contains("idx_travel_record_title_trgm");
        assertThat(
                planOf(
                    connection,
                    "select id from travel_record"
                        + " where lower(coalesce(content, '')) like '%부산%'"))
            .contains("idx_travel_record_content_trgm");
        assertThat(
                planOf(
                    connection,
                    "select id from travel_record_place where lower(place_name) like '%해운대%'"))
            .contains("idx_travel_record_place_name_trgm");
        assertThat(
                planOf(
                    connection,
                    "select id from travel_record_place"
                        + " where lower(coalesce(address, '')) like '%해운대%'"))
            .contains("idx_travel_record_place_address_trgm");
      }
    }
  }

  private String planOf(Connection connection, String sql) throws SQLException {
    StringBuilder plan = new StringBuilder();
    try (PreparedStatement statement = connection.prepareStatement("EXPLAIN " + sql);
        ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        plan.append(resultSet.getString(1)).append('\n');
      }
    }
    return plan.toString();
  }

  private boolean indexExists(Connection connection, String indexName) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT 1 FROM pg_indexes WHERE indexname = ?")) {
      statement.setString(1, indexName);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }
}
