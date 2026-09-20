package com.butingbe.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.butingbe.domain.auth.repository.OpaqueTokenRepository;
import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.UnauthenticatedException;
import com.butingbe.support.AbstractContainerTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class OpaqueTokenServiceTest extends AbstractContainerTest {

  @Autowired private OpaqueTokenService opaqueTokenService;

  @Autowired private OpaqueTokenRepository opaqueTokenRepository;

  @Autowired private UserRepository userRepository;

  @Test
  @DisplayName("Opaque access token은 원문이 아닌 해시로 저장되고 인증에 사용할 수 있다")
  void issueAndAuthenticate() {
    User user =
        userRepository.save(
            User.builder()
                .email("opaque@example.com")
                .provider("google")
                .providerId("google-opaque")
                .name(new Name("홍", "길동"))
                .nickname("opaque-user")
                .role(UserRole.USER)
                .build());

    OpaqueTokenService.IssuedOpaqueToken issuedToken = opaqueTokenService.issue(user);

    assertThat(issuedToken.accessToken()).isNotBlank();
    assertThat(issuedToken.tokenType()).isEqualTo("Bearer");
    assertThat(issuedToken.expiresIn())
        .isEqualTo(OpaqueTokenService.ACCESS_TOKEN_EXPIRES_IN_SECONDS);
    assertThat(opaqueTokensOf(user, com.butingbe.domain.auth.entity.OpaqueTokenType.ACCESS))
        .singleElement()
        .satisfies(
            token -> assertThat(token.getTokenHash()).isNotEqualTo(issuedToken.accessToken()));
    assertThat(issuedToken.refreshToken()).isNotBlank().isNotEqualTo(issuedToken.accessToken());
    assertThat(issuedToken.refreshExpiresIn())
        .isEqualTo(OpaqueTokenService.REFRESH_TOKEN_EXPIRES_IN_SECONDS);
    assertThat(opaqueTokensOf(user, com.butingbe.domain.auth.entity.OpaqueTokenType.REFRESH))
        .hasSize(1);
    assertThat(opaqueTokenService.authenticate(issuedToken.accessToken()))
        .hasValueSatisfying(
            authenticated -> assertThat(authenticated.getEmail()).isEqualTo(user.getEmail()));
  }

  @Test
  @DisplayName("같은 사용자의 active opaque token이 전달되면 새로 발급하지 않고 기존 토큰을 재사용한다")
  void reuseActiveOpaqueToken() {
    User user =
        userRepository.save(
            User.builder()
                .email("reuse-token@example.com")
                .provider("google")
                .providerId("google-reuse-token")
                .name(new Name("홍", "길동"))
                .nickname("reuse-token-user")
                .role(UserRole.USER)
                .build());

    OpaqueTokenService.IssuedOpaqueToken firstToken = opaqueTokenService.issue(user);
    OpaqueTokenService.IssuedOpaqueToken reusedToken =
        opaqueTokenService.issue(user, "Bearer " + firstToken.accessToken());

    assertThat(reusedToken.accessToken()).isEqualTo(firstToken.accessToken());
    assertThat(reusedToken.expiresIn()).isLessThanOrEqualTo(firstToken.expiresIn());
    assertThat(opaqueTokensOf(user, com.butingbe.domain.auth.entity.OpaqueTokenType.ACCESS))
        .hasSize(1);
    // 액세스를 재사용해도 리프레시는 새로 내준다. 이전 리프레시는 남지 않는다.
    assertThat(opaqueTokensOf(user, com.butingbe.domain.auth.entity.OpaqueTokenType.REFRESH))
        .hasSize(1);
    assertThat(reusedToken.refreshToken()).isNotEqualTo(firstToken.refreshToken());
  }

  @Test
  @DisplayName("같은 사용자가 다시 로그인하면 기존 active opaque token을 제거하고 새 토큰만 유지한다")
  void replaceActiveOpaqueTokenOnRepeatedLogin() {
    User user =
        userRepository.save(
            User.builder()
                .email("repeat-login@example.com")
                .provider("google")
                .providerId("google-repeat-login")
                .name(new Name("홍", "길동"))
                .nickname("repeat-login-user")
                .role(UserRole.USER)
                .build());

    OpaqueTokenService.IssuedOpaqueToken firstToken = opaqueTokenService.issue(user);
    OpaqueTokenService.IssuedOpaqueToken secondToken = opaqueTokenService.issue(user);

    assertThat(secondToken.accessToken()).isNotEqualTo(firstToken.accessToken());
    assertThat(opaqueTokensOf(user, com.butingbe.domain.auth.entity.OpaqueTokenType.ACCESS))
        .hasSize(1);
    assertThat(opaqueTokenService.authenticate(firstToken.accessToken())).isEmpty();
    assertThat(opaqueTokenService.authenticate(secondToken.accessToken()))
        .hasValueSatisfying(
            authenticated -> assertThat(authenticated.getId()).isEqualTo(user.getId()));
  }

  @Test
  @DisplayName("전달된 opaque token이 만료된 경우 새 토큰을 발급한다")
  void issueNewTokenWhenExistingTokenExpired() {
    User user =
        userRepository.save(
            User.builder()
                .email("expired-reissue@example.com")
                .provider("google")
                .providerId("google-expired-reissue")
                .name(new Name("홍", "길동"))
                .nickname("expired-reissue-user")
                .role(UserRole.USER)
                .build());
    String expiredRawToken = "expired-raw-token";
    opaqueTokenRepository.save(
        com.butingbe.domain.auth.entity.OpaqueToken.builder()
            .tokenHash(sha256(expiredRawToken))
            .user(user)
            .expiresAt(LocalDateTime.now().minusSeconds(1))
            .build());

    OpaqueTokenService.IssuedOpaqueToken issuedToken =
        opaqueTokenService.issue(user, "Bearer " + expiredRawToken);

    assertThat(issuedToken.accessToken()).isNotEqualTo(expiredRawToken);
    // 만료된 옛 토큰은 삭제 대상이 아니라 그대로 남고, 새 액세스·리프레시가 더해진다.
    assertThat(opaqueTokensOf(user)).hasSize(3);
    assertThat(opaqueTokenService.authenticate(issuedToken.accessToken())).contains(user);
  }

  @Test
  @DisplayName("만료되거나 폐기된 opaque token은 active 상태가 아니다")
  void inactiveOpaqueToken() {
    User user =
        User.builder()
            .email("inactive-token@example.com")
            .provider("google")
            .providerId("google-inactive-token")
            .name(new Name("홍", "길동"))
            .nickname("inactive-token-user")
            .role(UserRole.USER)
            .build();
    LocalDateTime now = LocalDateTime.now();
    com.butingbe.domain.auth.entity.OpaqueToken expiredToken =
        com.butingbe.domain.auth.entity.OpaqueToken.builder()
            .tokenHash("a".repeat(64))
            .user(user)
            .expiresAt(now.minusSeconds(1))
            .build();
    com.butingbe.domain.auth.entity.OpaqueToken revokedToken =
        com.butingbe.domain.auth.entity.OpaqueToken.builder()
            .tokenHash("b".repeat(64))
            .user(user)
            .expiresAt(now.plusDays(1))
            .build();

    revokedToken.revoke(now);

    assertThat(expiredToken.isActive(now)).isFalse();
    assertThat(revokedToken.isActive(now)).isFalse();
  }

  @Test
  @DisplayName("해시 알고리즘을 사용할 수 없으면 IllegalStateException이 발생한다")
  void unavailableHashAlgorithm() {
    OpaqueTokenService service =
        new OpaqueTokenService(mock(OpaqueTokenRepository.class), "UNKNOWN-HASH");

    assertThatThrownBy(() -> service.authenticate("raw-token"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SHA-256 is not available.");
  }

  private String sha256(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(hashed.length * 2);
      for (byte value : hashed) {
        builder.append("%02x".formatted(value));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  @DisplayName("리프레시 토큰으로 새 액세스 토큰을 받고, 쓰인 리프레시는 재사용되지 않는다")
  void refreshRotatesTokens() {
    User user = saveUser("refresh-rotate");

    OpaqueTokenService.IssuedOpaqueToken issued = opaqueTokenService.issue(user);
    OpaqueTokenService.IssuedOpaqueToken refreshed =
        opaqueTokenService.refresh(issued.refreshToken());

    assertThat(refreshed.accessToken()).isNotBlank().isNotEqualTo(issued.accessToken());
    assertThat(refreshed.refreshToken()).isNotBlank().isNotEqualTo(issued.refreshToken());
    assertThat(opaqueTokenService.authenticate(refreshed.accessToken())).contains(user);
    // 옛 액세스와 옛 리프레시는 모두 끊긴다.
    assertThat(opaqueTokenService.authenticate(issued.accessToken())).isEmpty();
    assertThatThrownBy(() -> opaqueTokenService.refresh(issued.refreshToken()))
        .isInstanceOf(UnauthenticatedException.class);
  }

  @Test
  @DisplayName("리프레시 토큰으로는 API를 호출할 수 없다")
  void refreshTokenCannotAuthenticate() {
    User user = saveUser("refresh-not-access");

    OpaqueTokenService.IssuedOpaqueToken issued = opaqueTokenService.issue(user);

    assertThat(opaqueTokenService.authenticate(issued.refreshToken())).isEmpty();
  }

  @Test
  @DisplayName("액세스 토큰으로는 재발급을 받을 수 없다")
  void accessTokenCannotRefresh() {
    User user = saveUser("access-not-refresh");

    OpaqueTokenService.IssuedOpaqueToken issued = opaqueTokenService.issue(user);

    assertThatThrownBy(() -> opaqueTokenService.refresh(issued.accessToken()))
        .isInstanceOf(UnauthenticatedException.class);
  }

  @Test
  @DisplayName("알 수 없거나 만료된 리프레시 토큰은 거부한다")
  void refreshRejectsUnknownOrExpiredToken() {
    User user = saveUser("refresh-expired");
    String expiredRaw = "expired-refresh-token";
    opaqueTokenRepository.save(
        com.butingbe.domain.auth.entity.OpaqueToken.builder()
            .tokenHash(sha256(expiredRaw))
            .user(user)
            .tokenType(com.butingbe.domain.auth.entity.OpaqueTokenType.REFRESH)
            .expiresAt(LocalDateTime.now().minusSeconds(1))
            .build());

    assertThatThrownBy(() -> opaqueTokenService.refresh("없는-토큰"))
        .isInstanceOf(UnauthenticatedException.class);
    assertThatThrownBy(() -> opaqueTokenService.refresh(expiredRaw))
        .isInstanceOf(UnauthenticatedException.class);
  }

  private User saveUser(String slug) {
    return userRepository.save(
        User.builder()
            .email(slug + "@example.com")
            .provider("google")
            .providerId("google-" + slug)
            .name(new Name("홍", "길동"))
            .nickname(slug)
            .role(UserRole.USER)
            .build());
  }

  private java.util.List<com.butingbe.domain.auth.entity.OpaqueToken> opaqueTokensOf(User user) {
    return opaqueTokenRepository.findAll().stream()
        .filter(token -> user.getId().equals(token.getUser().getId()))
        .toList();
  }

  /** 로그인 한 번이면 액세스와 리프레시가 한 개씩 남는다. */
  private java.util.List<com.butingbe.domain.auth.entity.OpaqueToken> opaqueTokensOf(
      User user, com.butingbe.domain.auth.entity.OpaqueTokenType type) {
    return opaqueTokensOf(user).stream().filter(token -> token.getTokenType() == type).toList();
  }

  @Test
  @DisplayName("Bearer 형식이 아니거나 토큰이 빈 Authorization 헤더는 재사용하지 않고 새로 발급한다")
  void issuesNewTokenWhenAuthorizationHeaderIsUnusable() {
    User user =
        userRepository.save(
            User.builder()
                .email("unusable-header@example.com")
                .provider("google")
                .providerId("google-unusable")
                .name(new Name("홍", "길동"))
                .nickname("unusable-user")
                .role(UserRole.USER)
                .build());

    OpaqueTokenService.IssuedOpaqueToken fromNull = opaqueTokenService.issue(user, null);
    OpaqueTokenService.IssuedOpaqueToken fromBasic =
        opaqueTokenService.issue(user, "Basic dXNlcjpwYXNz");
    OpaqueTokenService.IssuedOpaqueToken fromEmptyBearer =
        opaqueTokenService.issue(user, "Bearer  ");

    assertThat(fromNull.accessToken()).isNotBlank();
    assertThat(fromBasic.accessToken()).isNotBlank();
    assertThat(fromEmptyBearer.accessToken()).isNotBlank();
  }
}
