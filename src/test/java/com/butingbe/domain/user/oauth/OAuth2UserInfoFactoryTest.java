package com.butingbe.domain.user.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

class OAuth2UserInfoFactoryTest {

  @Test
  @DisplayName("Google OAuth2 attributes를 내부 사용자 정보로 변환한다")
  void google() {
    OAuth2UserInfo userInfo =
        OAuth2UserInfoFactory.from(
            "google",
            Map.of(
                "sub", "google-123",
                "email", "google@example.com",
                "name", "Google User",
                "given_name", "User",
                "family_name", "Google"));

    assertThat(userInfo.provider()).isEqualTo("google");
    assertThat(userInfo.providerId()).isEqualTo("google-123");
    assertThat(userInfo.email()).isEqualTo("google@example.com");
    assertThat(userInfo.safeNickname()).isEqualTo("Google User");
    assertThat(userInfo.toName().getFullName()).isEqualTo("GoogleUser");
  }

  @Test
  @DisplayName("Naver OAuth2 attributes를 response 내부 값에서 변환한다")
  void naver() {
    OAuth2UserInfo userInfo =
        OAuth2UserInfoFactory.from(
            "naver",
            Map.of(
                "response",
                Map.of(
                    "id", "naver-123",
                    "email", "naver@example.com",
                    "nickname", "네이버유저",
                    "name", "홍길동")));

    assertThat(userInfo.provider()).isEqualTo("naver");
    assertThat(userInfo.providerId()).isEqualTo("naver-123");
    assertThat(userInfo.email()).isEqualTo("naver@example.com");
    assertThat(userInfo.safeNickname()).isEqualTo("네이버유저");
  }

  @Test
  @DisplayName("Naver nickname이 없으면 name을 nickname으로 사용한다")
  void naverNicknameFallback() {
    OAuth2UserInfo userInfo =
        OAuth2UserInfoFactory.from(
            "naver",
            Map.of(
                "response",
                Map.of("id", "naver-456", "email", "naver-name@example.com", "name", "네이버이름")));

    assertThat(userInfo.nickname()).isEqualTo("네이버이름");
  }

  @Test
  @DisplayName("Kakao OAuth2 attributes를 kakao_account 내부 값에서 변환한다")
  void kakao() {
    OAuth2UserInfo userInfo =
        OAuth2UserInfoFactory.from(
            "kakao",
            Map.of(
                "id",
                12345,
                "kakao_account",
                Map.of("email", "kakao@example.com", "profile", Map.of("nickname", "카카오유저"))));

    assertThat(userInfo.provider()).isEqualTo("kakao");
    assertThat(userInfo.providerId()).isEqualTo("12345");
    assertThat(userInfo.email()).isEqualTo("kakao@example.com");
    assertThat(userInfo.safeNickname()).isEqualTo("카카오유저");
  }

  @Test
  @DisplayName("Kakao가 email을 제공하지 않으면 OAuth2 인증 예외가 발생한다")
  void kakaoWithoutEmail() {
    assertThatThrownBy(
            () ->
                OAuth2UserInfoFactory.from(
                    "kakao",
                    Map.of(
                        "id",
                        67890,
                        "kakao_account",
                        Map.of("profile", Map.of("nickname", "카카오닉")))))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  @DisplayName("provider id가 없으면 OAuth2 인증 예외가 발생한다")
  void invalidUserInfo() {
    assertThatThrownBy(
            () -> OAuth2UserInfoFactory.from("google", Map.of("email", "google@example.com")))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  @DisplayName("지원하지 않는 OAuth2 provider면 인증 예외가 발생한다")
  void unsupportedProvider() {
    assertThatThrownBy(() -> OAuth2UserInfoFactory.from("github", Map.of()))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  @DisplayName("Naver response가 없으면 필수 정보 검증에서 인증 예외가 발생한다")
  void naverWithoutResponse() {
    assertThatThrownBy(() -> OAuth2UserInfoFactory.from("naver", Map.of()))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  @DisplayName("Kakao account 정보가 없으면 OAuth2 인증 예외가 발생한다")
  void kakaoWithoutAccount() {
    assertThatThrownBy(() -> OAuth2UserInfoFactory.from("kakao", Map.of("id", 12345)))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }
}
