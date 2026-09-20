package com.butingbe.global.config;

import com.butingbe.domain.auth.security.OpaqueTokenAuthenticationFilter;
import com.butingbe.domain.user.oauth.CustomOAuth2UserService;
import com.butingbe.domain.user.oauth.OAuth2AuthenticationSuccessHandler;
import com.butingbe.global.error.RestAuthenticationErrorWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  /**
   * 인증 없이 열어 둘 경로. 여기에 없으면 인증이 필요하다.
   *
   * <p>기준은 코드다. 인증 주체를 아예 받지 않는 핸들러와, 주체를 받되 null 을 허용해 비로그인 응답을 만드는 조회만 넣었다. 새 API 를 여기에 추가하기 전에 그
   * 핸들러가 정말 비로그인으로 동작하는지 확인한다.
   */
  private static final String[] PUBLIC_GET_PATHS = {
    "/api/v1/places/**",
    "/api/v1/storage-locations",
    "/api/v1/chat/rooms/zone",
    "/api/v1/travel/team/invites/verify",
    "/api/v1/zone-titles",
    "/api/v1/zone-event-rounds/current",
    "/api/v1/zone-event-rounds/*/album",
    "/api/v1/zone-event-participations/*/comments",
    "/api/v1/travel-records",
    "/api/v1/travel-records/*",
    "/api/v1/travel-records/*/comments",
    "/api/v1/zone-events/active",
    "/api/v1/zone-events/*",
    "/api/v1/zone-events/*/album",
    "/api/v1/zones/*/album",
  };

  private final CustomOAuth2UserService customOAuth2UserService;
  private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
  private final OpaqueTokenAuthenticationFilter opaqueTokenAuthenticationFilter;
  private final RestAuthenticationErrorWriter authenticationErrorWriter;

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> {})
        .authorizeHttpRequests(
            authorize ->
                authorize
                    // 순서가 의미를 가진다. /travel-records/me 는 /travel-records/* 에도 걸리므로 먼저 막는다.
                    .requestMatchers("/api/v1/travel-records/me", "/api/v1/travel-records/me/**")
                    .authenticated()
                    .requestMatchers("/api/v1/auth/**", "/error")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET, "/docs/**", "/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS)
                    .permitAll()
                    // 운영자 API 는 서비스의 OperatorAuthorization 외에 URL 단에서도 막는다.
                    .requestMatchers("/api/v1/admin/**")
                    .hasAnyRole("ADMIN", "MANAGER")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            handling ->
                handling
                    .authenticationEntryPoint(authenticationErrorWriter)
                    .accessDeniedHandler(authenticationErrorWriter))
        .addFilterBefore(
            opaqueTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    if (clientRegistrationRepository.getIfAvailable() != null) {
      http.oauth2Login(
          oauth2 ->
              oauth2
                  .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                  .successHandler(oAuth2AuthenticationSuccessHandler));
    }

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    configuration.addAllowedOrigin("http://localhost:3000");
    configuration.addAllowedOrigin("http://localhost:3001");
    configuration.addAllowedOrigin("https://dev.buting.store");
    configuration.addAllowedOrigin("https://buting.store");
    configuration.addAllowedMethod("*"); // 모든 HTTP Method 일단 허용 (GET, POST 등)
    configuration.addAllowedHeader("*"); // 모든 헤더 허용

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
