package com.butingbe.domain.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.butingbe.domain.auth.service.OpaqueTokenService;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class OpaqueTokenAuthenticationFilterTest {

  private static final String ADMIN_TOKEN = "local-admin-token";

  private final OpaqueTokenService opaqueTokenService = mock(OpaqueTokenService.class);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("local 프로필에서 관리자 토큰을 보내면 ADMIN으로 인증된다")
  void authenticatesAdminUnderLocalProfile() throws Exception {
    doFilter(filter("local", ADMIN_TOKEN), ADMIN_TOKEN);

    AuthenticatedUser principal = currentPrincipal();
    assertThat(principal).isNotNull();
    assertThat(principal.isDevelopmentAdmin()).isTrue();
    assertThat(principal.id()).isNull();
  }

  @Test
  @DisplayName("local 이외의 프로필에서는 같은 관리자 토큰이 인증되지 않는다")
  void ignoresAdminTokenOutsideLocalProfile() throws Exception {
    given(opaqueTokenService.authenticate(anyString())).willReturn(Optional.empty());

    doFilter(filter("prod", ADMIN_TOKEN), ADMIN_TOKEN);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("활성 프로필이 없으면 관리자 토큰이 인증되지 않는다")
  void ignoresAdminTokenWithoutAnyProfile() throws Exception {
    given(opaqueTokenService.authenticate(anyString())).willReturn(Optional.empty());

    doFilter(filter(null, ADMIN_TOKEN), ADMIN_TOKEN);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("local 프로필이어도 토큰이 다르면 인증되지 않는다")
  void rejectsWrongAdminTokenUnderLocalProfile() throws Exception {
    given(opaqueTokenService.authenticate(anyString())).willReturn(Optional.empty());

    doFilter(filter("local", ADMIN_TOKEN), "wrong-token");

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("관리자 토큰이 비어 있으면 빈 Bearer 토큰으로도 인증되지 않는다")
  void rejectsBlankAdminToken() throws Exception {
    given(opaqueTokenService.authenticate(anyString())).willReturn(Optional.empty());

    doFilter(filter("local", ""), "");

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("유효한 Opaque 토큰은 해당 사용자로 인증된다")
  void authenticatesOpaqueTokenUser() throws Exception {
    UUID userId = UUID.randomUUID();
    User user = mock(User.class);
    given(user.getId()).willReturn(userId);
    given(user.getEmail()).willReturn("user@example.com");
    given(user.getNickname()).willReturn("user");
    given(user.getRole()).willReturn(UserRole.USER);
    given(opaqueTokenService.authenticate("opaque-token")).willReturn(Optional.of(user));

    doFilter(filter("local", ADMIN_TOKEN), "opaque-token");

    AuthenticatedUser principal = currentPrincipal();
    assertThat(principal).isNotNull();
    assertThat(principal.id()).isEqualTo(userId);
    assertThat(principal.isDevelopmentAdmin()).isFalse();
  }

  @Test
  @DisplayName("Authorization 헤더가 없으면 인증을 시도하지 않는다")
  void skipsWithoutAuthorizationHeader() throws Exception {
    filter("local", ADMIN_TOKEN)
        .doFilter(
            new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  private OpaqueTokenAuthenticationFilter filter(String profile, String adminToken) {
    MockEnvironment environment = new MockEnvironment();
    if (profile != null) {
      environment.setActiveProfiles(profile);
    }
    return new OpaqueTokenAuthenticationFilter(opaqueTokenService, environment, adminToken);
  }

  private void doFilter(OpaqueTokenAuthenticationFilter filter, String bearerToken)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
  }

  private AuthenticatedUser currentPrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication == null ? null : (AuthenticatedUser) authentication.getPrincipal();
  }
}
