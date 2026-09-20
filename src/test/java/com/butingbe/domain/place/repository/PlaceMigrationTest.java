package com.butingbe.domain.place.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/** V51이 만드는 장소 카탈로그 테이블의 유니크·CHECK 제약을 실제 PostgreSQL로 검증한다. */
@Tag("integration")
class PlaceMigrationTest {

  @Test
  @DisplayName("place 테이블은 provider 식별자 유니크와 권역·시간대 제약을 가진다")
  void migratePlace() throws Exception {
    try (PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("buting_place_test")
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

        insert(connection, "TOUR_API", "126508", "감천문화마을", "WESTERN_BUSAN", "MORNING", 90);

        // 같은 provider 식별자는 두 번 저장되지 않는다 — 재동기화가 중복을 만들지 않아야 한다
        assertThat(
                catchThrowable(
                    () ->
                        insert(
                            connection,
                            "TOUR_API",
                            "126508",
                            "감천문화마을",
                            "WESTERN_BUSAN",
                            "MORNING",
                            90)))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("uk_place_provider_place_id");

        // 다른 provider면 같은 식별자를 써도 된다
        insert(connection, "GOOGLE", "126508", "감천문화마을", "WESTERN_BUSAN", "MORNING", 90);

        // 6권역 밖 값은 CHECK로 거부
        assertThat(
                catchThrowable(
                    () -> insert(connection, "TOUR_API", "1", "테스트", "NOWHERE", "MORNING", 60)))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("ck_place_zone_id");

        // 정의되지 않은 시간대도 거부
        assertThat(
                catchThrowable(
                    () -> insert(connection, "TOUR_API", "2", "테스트", "YEONGDO", "MIDNIGHT", 60)))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("ck_place_time_slot");

        // 체류 시간 0분은 의미가 없다
        assertThat(
                catchThrowable(
                    () -> insert(connection, "TOUR_API", "3", "테스트", "YEONGDO", "MORNING", 0)))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("ck_place_dwell_minutes");

        // 보강 컬럼은 적재 직후 비어 있을 수 있다
        insertMinimal(connection, "TOUR_API", "4", "좌표만 있는 장소");
      }
    }
  }

  private void insert(
      Connection connection,
      String provider,
      String providerPlaceId,
      String name,
      String zoneId,
      String timeSlot,
      int dwellMinutes)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
            INSERT INTO place (
              provider, provider_place_id, name, latitude, longitude,
              zone_id, preferred_time_slot, dwell_minutes)
            VALUES (?, ?, ?, 35.0975, 129.0107, ?, ?, ?)
            """)) {
      statement.setString(1, provider);
      statement.setString(2, providerPlaceId);
      statement.setString(3, name);
      statement.setString(4, zoneId);
      statement.setString(5, timeSlot);
      statement.setInt(6, dwellMinutes);
      statement.executeUpdate();
    }
  }

  private void insertMinimal(
      Connection connection, String provider, String providerPlaceId, String name)
      throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO place (provider, provider_place_id, name) VALUES ('%s', '%s', '%s')"
              .formatted(provider, providerPlaceId, name));
    }
  }
}
