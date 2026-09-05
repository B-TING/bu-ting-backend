package com.butingbe.domain.route.google;

import com.butingbe.domain.route.RouteProvider;
import com.butingbe.domain.route.dto.RouteLeg;
import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.travel.entity.TransportType;
import org.springframework.web.client.RestClient;

/**
 * Google Routes API로 실제 이동 거리와 소요 시간을 조회한다.
 *
 * <p>스프링 빈으로 자동 등록하지 않는다. 키가 설정된 경우에만 {@code RouteProviderConfig}가 만들어 대표 provider의 delegate로 쓰고,
 * 실패는 {@code ResilientRouteProvider}가 좌표 계산으로 흡수한다. 단독으로 쓰지 않는 이유는 한국 안에서 자동차·도보 경로를 돌려주지 않기 때문이다.
 *
 * <p>경로를 찾지 못하면(대중교통이 아닌 이동 수단, 또는 결과 없음) {@link IllegalStateException}을 던진다. 호출자는 이를 실패로 보고 폴백한다.
 */
public class GoogleRoutesProvider implements RouteProvider {

  private final RestClient restClient;
  private final String baseUrl;
  private final String apiKey;

  public GoogleRoutesProvider(RestClient restClient, String baseUrl, String apiKey) {
    this.restClient = restClient;
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
  }

  @Override
  public RouteLeg leg(RoutePoint from, RoutePoint to, TransportType transportType) {
    TransportType mode = transportType == null ? TransportType.PUBLIC_TRANSPORT : transportType;

    GoogleRoutesResponse response =
        restClient
            .post()
            .uri(baseUrl + ":computeRoutes")
            .header("X-Goog-Api-Key", apiKey)
            .header("X-Goog-FieldMask", "routes.distanceMeters,routes.duration")
            .body(
                new GoogleRoutesRequest(
                    GoogleRoutesRequest.waypoint(from.latitude(), from.longitude()),
                    GoogleRoutesRequest.waypoint(to.latitude(), to.longitude()),
                    travelMode(mode),
                    "ko"))
            .retrieve()
            .body(GoogleRoutesResponse.class);

    if (response == null || response.routes() == null || response.routes().isEmpty()) {
      throw new IllegalStateException("Google Routes returned no route.");
    }

    GoogleRoutesResponse.Route route = response.routes().get(0);
    int distanceMeters = route.distanceMeters() == null ? 0 : route.distanceMeters();
    int durationMinutes = (int) Math.ceil(parseSeconds(route.duration()) / 60.0);
    return new RouteLeg(from, to, mode, distanceMeters, durationMinutes);
  }

  private String travelMode(TransportType transportType) {
    return switch (transportType) {
      case WALK -> "WALK";
      case PUBLIC_TRANSPORT -> "TRANSIT";
      case CAR -> "DRIVE";
    };
  }

  /** "921s" 형태의 소요 시간을 초로 바꾼다. 값이 없거나 형식이 다르면 0초로 본다. */
  private long parseSeconds(String duration) {
    if (duration == null || !duration.endsWith("s")) {
      return 0;
    }
    try {
      return Long.parseLong(duration.substring(0, duration.length() - 1));
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
