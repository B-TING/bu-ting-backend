package com.butingbe.domain.storage.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * V50이 V29 시드의 이름 기반 매핑으로 생긴 락커 중복·오매핑을 정리하는지 실제 PostgreSQL로 검증한다.
 *
 * <p>V48까지 적용한 뒤 시드를 한 번 더 돌린 상황(운영에서 실제로 일어난 중복)을 만들고 V50을 적용한다.
 */
@Tag("integration")
class LockerDataCleanupMigrationTest {

  @Test
  @DisplayName("V50은 중복·호선 오매핑·요금 오매핑을 정리하고 재발을 막는다")
  void cleanupLockerData() throws Exception {
    try (PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("buting_locker_test")
            .withUsername("test_user")
            .withPassword("test_password")) {
      postgres.start();

      migrate(postgres, "48");

      try (Connection connection = connect(postgres)) {
        duplicateSeedRows(connection);
        assertThat(duplicateGroups(connection)).isPositive();
      }

      migrate(postgres, null);

      try (Connection connection = connect(postgres)) {
        // 같은 (역, 위치, 업체) 조합은 하나만 남는다
        assertThat(duplicateGroups(connection)).isZero();

        // 환승역은 번호가 낮은 호선에만 락커가 붙는다
        assertThat(lockerCount(connection, "수영", "3호선")).isZero();
        assertThat(lockerCount(connection, "수영", "2호선")).isPositive();
        assertThat(lockerCount(connection, "미남(광혜병원)", "4호선")).isZero();
        assertThat(lockerCount(connection, "미남(광혜병원)", "3호선")).isPositive();

        // 부산대양산캠퍼스 락커의 요금이 복구되고, 남양산에 잘못 붙은 세트는 사라진다
        assertThat(feeCount(connection, "부산대양산캠퍼스")).isEqualTo(6);
        assertThat(
                count(
                    connection,
                    """
                    SELECT COUNT(*) FROM locker_fee
                    WHERE schedule_type = 'WEEKEND' AND locker_size = 'SMALL'
                      AND amount BETWEEN 2751 AND 2775
                    """))
            .isZero();

        // 락커 정보가 없는 역 이름이 남지 않는다
        assertThat(
                count(
                    connection,
                    """
                    SELECT COUNT(*) FROM (
                      SELECT s.name FROM station s
                      GROUP BY s.name
                      HAVING COUNT(*) FILTER (
                        WHERE EXISTS (SELECT 1 FROM locker_location l WHERE l.station_id = s.id)
                      ) = 0
                    ) missing
                    """))
            .isZero();

        // 유니크 인덱스가 같은 시드의 재실행을 막는다
        assertThat(catchThrowable(() -> duplicateSeedRows(connection)))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("uk_locker_location_station_detail_company");
      }
    }
  }

  private void migrate(PostgreSQLContainer<?> postgres, String target) {
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration")
        .target(target == null ? MigrationVersion.LATEST : MigrationVersion.fromVersion(target))
        .load()
        .migrate();
  }

  private Connection connect(PostgreSQLContainer<?> postgres) throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  /** 시드를 한 번 더 돌린 것과 같은 상태를 만든다. */
  private void duplicateSeedRows(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO locker_location (
            station_id, location_detail, small_count, medium_count, large_count,
            extra_large_count, company, raw_fee_text)
          SELECT station_id, location_detail, small_count, medium_count, large_count,
                 extra_large_count, company, raw_fee_text
          FROM locker_location
          """);
    }
  }

  private long duplicateGroups(Connection connection) throws SQLException {
    return count(
        connection,
        """
        SELECT COUNT(*) FROM (
          SELECT 1 FROM locker_location
          GROUP BY station_id, COALESCE(location_detail, ''), COALESCE(company, '')
          HAVING COUNT(*) > 1
        ) dupes
        """);
  }

  private long lockerCount(Connection connection, String stationName, String line)
      throws SQLException {
    return count(
        connection,
        """
        SELECT COUNT(*) FROM locker_location l
        JOIN station s ON s.id = l.station_id
        WHERE s.name = '%s' AND s.line = '%s'
        """
            .formatted(stationName, line));
  }

  private long feeCount(Connection connection, String stationName) throws SQLException {
    return count(
        connection,
        """
        SELECT COUNT(*) FROM locker_fee f
        JOIN locker_location l ON l.id = f.locker_location_id
        JOIN station s ON s.id = l.station_id
        WHERE s.name = '%s'
        """
            .formatted(stationName));
  }

  private long count(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement();
        var resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      return resultSet.getLong(1);
    }
  }
}
