package com.butingbe.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.butingbe.domain.auth.dto.request.OAuthLoginReqDto;
import com.butingbe.domain.auth.oauth.OAuthProviderTokenVerifier;
import com.butingbe.domain.user.dto.response.OAuth2LoginResDto;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.oauth.OAuth2UserInfo;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OAuthLoginServiceTest {

  private final OAuthProviderTokenVerifier oAuthProviderTokenVerifier =
      mock(OAuthProviderTokenVerifier.class);
  private final UserRepository userRepository = mock(UserRepository.class);
  private final OpaqueTokenService opaqueTokenService = mock(OpaqueTokenService.class);
  private final OAuthLoginService oAuthLoginService =
      new OAuthLoginService(oAuthProviderTokenVerifier, userRepository, opaqueTokenService);

  @Test
  @DisplayName("provider token 검증 후 기존 이메일 회원에 provider를 연결하고 opaque token을 발급한다")
  void loginWithExistingEmailUser() {
    OAuthLoginReqDto request =
        new OAuthLoginReqDto("google", "GOOGLE_AUTHORIZATION_CODE", null, null);
    OAuth2UserInfo userInfo =
        new OAuth2UserInfo(
            "google", "google-123", "oauth@example.com", "구글유저", "길동", "홍", Map.of());
    User user = mock(User.class);
    UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    given(
            oAuthProviderTokenVerifier.verify(
                request.provider(),
                request.providerToken(),
                request.redirectUri(),
                request.codeVerifier()))
        .willReturn(userInfo);
    given(userRepository.findByProviderAndProviderId("google", "google-123"))
        .willReturn(Optional.empty());
    given(userRepository.findByEmail("oauth@example.com")).willReturn(Optional.of(user));
    given(user.getId()).willReturn(userId);
    given(user.getEmail()).willReturn("oauth@example.com");
    given(user.getNickname()).willReturn("기존닉네임");
    given(user.getProvider()).willReturn("google");
    given(opaqueTokenService.issue(user, null))
        .willReturn(
            new OpaqueTokenService.IssuedOpaqueToken(
                "opaque-token", "Bearer", 3600, LocalDateTime.now().plusHours(1)));

    OAuth2LoginResDto response = oAuthLoginService.login(request);

    verify(user).linkOAuthProvider("google", "google-123");
    assertThat(response.userId()).isEqualTo(userId.toString());
    assertThat(response.email()).isEqualTo("oauth@example.com");
    assertThat(response.nickname()).isEqualTo("기존닉네임");
    assertThat(response.accessToken()).isEqualTo("opaque-token");
  }

  @Test
  @DisplayName("provider와 email이 모두 신규이면 OAuth 사용자로 회원을 생성하고 opaque token을 발급한다")
  void loginWithNewOAuthUser() {
    OAuthLoginReqDto request =
        new OAuthLoginReqDto("kakao", "KAKAO_AUTHORIZATION_CODE", null, null);
    OAuth2UserInfo userInfo =
        new OAuth2UserInfo(
            "kakao", "12345", "kakao@example.com", "카카오유저", "카카오유저", "kakao", Map.of());
    User savedUser = mock(User.class);
    UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

    given(
            oAuthProviderTokenVerifier.verify(
                request.provider(),
                request.providerToken(),
                request.redirectUri(),
                request.codeVerifier()))
        .willReturn(userInfo);
    given(userRepository.findByProviderAndProviderId("kakao", "12345"))
        .willReturn(Optional.empty());
    given(userRepository.findByEmail("kakao@example.com")).willReturn(Optional.empty());
    given(userRepository.save(any(User.class))).willReturn(savedUser);
    given(savedUser.getId()).willReturn(userId);
    given(savedUser.getEmail()).willReturn("kakao@example.com");
    given(savedUser.getNickname()).willReturn("카카오유저");
    given(savedUser.getProvider()).willReturn("kakao");
    given(opaqueTokenService.issue(savedUser, null))
        .willReturn(
            new OpaqueTokenService.IssuedOpaqueToken(
                "new-opaque-token", "Bearer", 3600, LocalDateTime.now().plusHours(1)));

    OAuth2LoginResDto response = oAuthLoginService.login(request);

    assertThat(response.email()).isEqualTo("kakao@example.com");
    assertThat(response.nickname()).isEqualTo("카카오유저");
    assertThat(response.provider()).isEqualTo("kakao");
    assertThat(response.accessToken()).isEqualTo("new-opaque-token");
    verify(userRepository).save(any(User.class));
  }

  @Test
  @DisplayName("provider가 이메일을 제공하지 않으면 회원을 생성하지 않고 인증 실패 처리한다")
  void loginFailsWhenOAuthUserHasNoEmail() {
    OAuthLoginReqDto request =
        new OAuthLoginReqDto("kakao", "KAKAO_AUTHORIZATION_CODE", null, null);
    OAuth2UserInfo userInfo =
        new OAuth2UserInfo("kakao", "99999", null, "카카오유저", "카카오유저", "kakao", Map.of());

    given(
            oAuthProviderTokenVerifier.verify(
                request.provider(),
                request.providerToken(),
                request.redirectUri(),
                request.codeVerifier()))
        .willReturn(userInfo);

    assertThatThrownBy(() -> oAuthLoginService.login(request))
        .isInstanceOf(UnauthenticatedException.class);
    verify(userRepository, never()).findByProviderAndProviderId(any(), any());
    verify(userRepository, never()).findByEmail(any());
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  @DisplayName("이미 provider로 연결된 회원이면 이메일 조회 없이 opaque token을 발급한다")
  void loginWithExistingProviderUser() {
    OAuthLoginReqDto request =
        new OAuthLoginReqDto("naver", "NAVER_AUTHORIZATION_CODE", null, null);
    OAuth2UserInfo userInfo =
        new OAuth2UserInfo(
            "naver", "naver-123", "naver@example.com", "네이버유저", "네이버유저", "naver", Map.of());
    User user = mock(User.class);
    UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");

    given(
            oAuthProviderTokenVerifier.verify(
                request.provider(),
                request.providerToken(),
                request.redirectUri(),
                request.codeVerifier()))
        .willReturn(userInfo);
    given(userRepository.findByProviderAndProviderId("naver", "naver-123"))
        .willReturn(Optional.of(user));
    given(user.getId()).willReturn(userId);
    given(user.getEmail()).willReturn("naver@example.com");
    given(user.getNickname()).willReturn("네이버유저");
    given(user.getProvider()).willReturn("naver");
    given(opaqueTokenService.issue(user, null))
        .willReturn(
            new OpaqueTokenService.IssuedOpaqueToken(
                "provider-token", "Bearer", 3600, LocalDateTime.now().plusHours(1)));

    OAuth2LoginResDto response = oAuthLoginService.login(request);

    assertThat(response.email()).isEqualTo("naver@example.com");
    assertThat(response.accessToken()).isEqualTo("provider-token");
  }
}
