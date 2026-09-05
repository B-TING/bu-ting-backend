package com.butingbe.domain.route.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.travel.entity.TransportType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleRoutesProviderTest {

  private static final RoutePoint FROM = RoutePoint.of("부산역", 35.1151, 129.0413);
  private static final RoutePoint TO = RoutePoint.of("광안리", 35.1532, 129.1186);
  private static final String BASE_URL = "https://routes.example.com/directions/v2";

  @Test
  @DisplayName("computeRoutes 응답의 거리와 초 단위 시간을 구간으로 변환한다")
  void mapsComputeRoutesResponse() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    GoogleRoutesProvider provider =
        new GoogleRoutesProvider(builder.build(), BASE_URL, "GOOGLE_KEY");

    server
        .expect(requestTo(BASE_URL + ":computeRoutes"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Goog-Api-Key", "GOOGLE_KEY"))
        .andRespond(
            withSuccess(
                """
                {"routes":[{"distanceMeters":9805,"duration":"2880s"}]}
                """,
                MediaType.APPLICATION_JSON));

    RouteLeg leg = provider.leg(FROM, TO, TransportType.PUBLIC_TRANSPORT);

    assertThat(leg.distanceMeters()).isEqualTo(9805);
    assertThat(leg.durationMinutes()).isEqualTo(48);
    assertThat(leg.transportType()).isEqualTo(TransportType.PUBLIC_TRANSPORT);
    server.verify();
  }

  @Test
  @DisplayName("경로를 찾지 못하면(한국의 자동차·도보처럼) 예외를 던진다")
  void throwsWhenNoRouteReturned() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    GoogleRoutesProvider provider =
        new GoogleRoutesProvider(builder.build(), BASE_URL, "GOOGLE_KEY");

    server
        .expect(method(HttpMethod.POST))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> provider.leg(FROM, TO, TransportType.CAR))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Google Routes returned no route.");
    server.verify();
  }

  @Test
  @DisplayName("빈 routes 배열도 경로 없음으로 처리한다")
  void throwsWhenRoutesAreEmpty() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    GoogleRoutesProvider provider =
        new GoogleRoutesProvider(builder.build(), BASE_URL, "GOOGLE_KEY");

    server
        .expect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                """
            {"routes":[]}
            """,
                MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> provider.leg(FROM, TO, TransportType.WALK))
        .isInstanceOf(IllegalStateException.class);
    server.verify();
  }

  @Test
  @DisplayName("Rate Limit(429)은 예외로 전파되어 상위에서 폴백된다")
  void propagatesRateLimit() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    GoogleRoutesProvider provider =
        new GoogleRoutesProvider(builder.build(), BASE_URL, "GOOGLE_KEY");

    server.expect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

    assertThatThrownBy(() -> provider.leg(FROM, TO, TransportType.PUBLIC_TRANSPORT))
        .isInstanceOf(RuntimeException.class);
    server.verify();
  }

  @Test
  @DisplayName("거리나 시간이 비어 있어도 0으로 채워 구간을 만든다")
  void toleratesMissingFields() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    GoogleRoutesProvider provider =
        new GoogleRoutesProvider(builder.build(), BASE_URL, "GOOGLE_KEY");

    server
        .expect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                """
                {"routes":[{"distanceMeters":500,"duration":"not-seconds"}]}
                """,
                MediaType.APPLICATION_JSON));

    RouteLeg leg = provider.leg(FROM, TO, TransportType.PUBLIC_TRANSPORT);

    assertThat(leg.distanceMeters()).isEqualTo(500);
    assertThat(leg.durationMinutes()).isZero();
    server.verify();
  }

  @Test
  @DisplayName("소요 시간 필드 자체가 없으면 0분으로 채운다")
  void toleratesAbsentDuration() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    GoogleRoutesProvider provider =
        new GoogleRoutesProvider(builder.build(), BASE_URL, "GOOGLE_KEY");

    server
        .expect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                """
                {"routes":[{"distanceMeters":1000}]}
                """,
                MediaType.APPLICATION_JSON));

    RouteLeg leg = provider.leg(FROM, TO, TransportType.PUBLIC_TRANSPORT);

    assertThat(leg.distanceMeters()).isEqualTo(1000);
    assertThat(leg.durationMinutes()).isZero();
    server.verify();
  }
}
