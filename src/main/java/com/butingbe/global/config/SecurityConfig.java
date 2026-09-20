package com.butingbe.global.config;

import com.butingbe.domain.auth.security.OpaqueTokenAuthenticationFilter;
import com.butingbe.domain.user.oauth.CustomOAuth2UserService;
import com.butingbe.domain.user.oauth.OAuth2AuthenticationSuccessHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

  /** HTTP CORS와 WebSocket 핸드셰이크가 함께 쓰는 허용 오리진 목록. */
  public static final List<String> ALLOWED_ORIGINS =
      List.of(
          "http://localhost:3000",
          "http://localhost:3001",
          "https://dev.buting.store",
          "https://buting.store");

  private final CustomOAuth2UserService customOAuth2UserService;
  private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
  private final OpaqueTokenAuthenticationFilter opaqueTokenAuthenticationFilter;

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> {})
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers("/api/v1/**", "/actuator/**", "/error")
                    .permitAll()
                    .anyRequest()
                    .permitAll())
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

    ALLOWED_ORIGINS.forEach(configuration::addAllowedOrigin);
    configuration.addAllowedMethod("*"); // 모든 HTTP Method 일단 허용 (GET, POST 등)
    configuration.addAllowedHeader("*"); // 모든 헤더 허용

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
