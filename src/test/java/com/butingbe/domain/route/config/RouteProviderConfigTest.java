package com.butingbe.domain.route.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.route.HaversineRouteProvider;
import com.butingbe.domain.route.ResilientRouteProvider;
import com.butingbe.domain.route.RouteProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** route.google.enabled 설정에 따라 대표 provider가 갈리는지 검증한다. */
class RouteProviderConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              org.springframework.boot.autoconfigure.AutoConfigurations.of(
                  PropertyPlaceholderAutoConfiguration.class))
          .withBean(HaversineRouteProvider.class)
          .withUserConfiguration(RouteProviderConfig.class);

  @Test
  @DisplayName("기본값(설정 없음)에서는 좌표 계산 provider를 그대로 쓴다")
  void usesHaversineByDefault() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(RouteProvider.class);
          assertThat(context.getBean(RouteProvider.class))
              .isInstanceOf(HaversineRouteProvider.class);
        });
  }

  @Test
  @DisplayName("google.enabled=true면 폴백 계층을 대표 provider로 등록한다")
  void usesResilientWhenGoogleEnabled() {
    runner
        .withPropertyValues("route.google.enabled=true", "route.google.api-key=TEST_KEY")
        .run(
            context -> {
              assertThat(context.getBeansOfType(RouteProvider.class)).hasSize(2);
              // @Primary가 폴백 계층이어야 한다.
              assertThat(context.getBean(RouteProvider.class))
                  .isInstanceOf(ResilientRouteProvider.class);
            });
  }
}
