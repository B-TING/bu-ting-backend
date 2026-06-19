package com.butingbe.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserOAuthProviderTest {

  @Test
  @DisplayName("기존 회원에 OAuth provider를 연결해도 프로필 정보는 유지된다")
  void linkOAuthProviderKeepsExistingProfile() {
    User user =
        User.builder()
            .email("user@example.com")
            .name(new Name("홍", "길동"))
            .nickname("기존닉네임")
            .role(UserRole.USER)
            .build();

    user.linkOAuthProvider("google", "google-123");

    assertThat(user.getProvider()).isEqualTo("google");
    assertThat(user.getProviderId()).isEqualTo("google-123");
    assertThat(user.getEmail()).isEqualTo("user@example.com");
    assertThat(user.getName().getFullName()).isEqualTo("홍길동");
    assertThat(user.getNickname()).isEqualTo("기존닉네임");
  }
}
