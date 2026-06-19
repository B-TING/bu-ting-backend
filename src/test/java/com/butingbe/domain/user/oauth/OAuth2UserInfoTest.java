package com.butingbe.domain.user.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OAuth2UserInfoTest {

  @Test
  @DisplayName("이름 정보가 비어 있으면 provider와 nickname으로 Name을 만든다")
  void toNameFallsBackToProviderAndNickname() {
    OAuth2UserInfo userInfo =
        new OAuth2UserInfo("kakao", "12345", "kakao@example.com", " 카카오닉네임 ", "", null, Map.of());

    assertThat(userInfo.toName().getFullName()).isEqualTo("kakao카카오닉네임");
  }

  @Test
  @DisplayName("nickname이 비어 있으면 email local-part를 안전한 nickname으로 사용한다")
  void safeNicknameFallsBackToEmailLocalPart() {
    OAuth2UserInfo userInfo =
        new OAuth2UserInfo(
            "google", "google-123", "google@example.com", "", "Google", "User", Map.of());

    assertThat(userInfo.safeNickname()).isEqualTo("google");
  }

  @Test
  @DisplayName("nickname과 email이 모두 없으면 provider와 provider id로 안전한 nickname을 만든다")
  void safeNicknameFallsBackToProviderId() {
    OAuth2UserInfo userInfo =
        new OAuth2UserInfo("kakao", "12345", null, null, null, null, Map.of());

    assertThat(userInfo.safeNickname()).isEqualTo("kakao-12345");
    assertThat(userInfo.toName().getFullName()).isEqualTo("kakaokakao-12345");
  }
}
