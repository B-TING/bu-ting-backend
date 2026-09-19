package com.butingbe.domain.route.config;

import com.butingbe.domain.route.CachingRouteProvider;
import com.butingbe.domain.route.HaversineRouteProvider;
import com.butingbe.domain.route.ResilientRouteProvider;
import com.butingbe.domain.route.RouteProvider;
import com.butingbe.domain.route.google.GoogleRoutesProvider;
import com.butingbe.domain.route.repository.PlaceTravelTimeRepository;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 경로 provider를 조립한다.
 *
 * <p>기본은 좌표 계산({@link HaversineRouteProvider})만 쓴다. {@code route.google.enabled=true}이고 키가 있을 때만
 * Google Routes를 주 provider로 얹고, 실패는 좌표 계산으로 흡수하는 {@link ResilientRouteProvider}를 대표 provider로
 * 등록한다.
 *
 * <p>이렇게 두면 외부 API를 붙일지 여부가 설정 하나로 갈리고, 켜더라도 장애가 서비스로 번지지 않는다. 키가 없으면 Haversine 빈이 유일하므로 그대로 쓰인다.
 *
 * <p>외부 API를 켤 때는 {@link CachingRouteProvider}로 한 번 더 감싼다. 같은 구간을 반복 조회하지 않기 위해서이고, 좌표 계산만 쓰는 구성에는
 * 얹지 않는다.
 */
@Configuration
public class RouteProviderConfig {

  @Bean
  @Primary
  @ConditionalOnProperty(name = "route.google.enabled", havingValue = "true")
  public RouteProvider resilientRouteProvider(
      HaversineRouteProvider haversineRouteProvider,
      PlaceTravelTimeRepository placeTravelTimeRepository,
      @Value("${route.google.base-url:https://routes.googleapis.com/directions/v2}") String baseUrl,
      @Value("${route.google.api-key:${GOOGLE_PLACES_API_KEY:}}") String apiKey,
      @Value("${route.google.connect-timeout-seconds:3}") long connectTimeoutSeconds,
      @Value("${route.google.read-timeout-seconds:5}") long readTimeoutSeconds,
      @Value("${route.cache.ttl-days:30}") long cacheTtlDays) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
    requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
    RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();

    GoogleRoutesProvider google = new GoogleRoutesProvider(restClient, baseUrl, apiKey);
    ResilientRouteProvider resilient = new ResilientRouteProvider(google, haversineRouteProvider);
    // 캐시는 외부 API를 켰을 때만 얹는다. 좌표 계산만 쓰는 구성에서는 계산이 조회보다 싸다.
    return new CachingRouteProvider(
        resilient, placeTravelTimeRepository, Duration.ofDays(cacheTtlDays));
  }
}
