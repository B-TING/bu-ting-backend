package com.butingbe.domain.user.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

class CustomOAuth2UserServiceTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate =
      mock(OAuth2UserService.class);
  private final CustomOAuth2UserService service =
      new CustomOAuth2UserService(userRepository, delegate);

  @Test
  @DisplayName("OAuth2 user info의 이메일과 일치하는 기존 회원에 provider를 연결하고 principal attributes를 반환한다")
  void loadUserLinksExistingEmailUser() {
    OAuth2UserRequest request = userRequest("kakao");
    OAuth2User oauth2User =
        new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of(
                "id",
                12345,
                "kakao_account",
                Map.of("email", "kakao@example.com", "profile", Map.of("nickname", "카카오유저"))),
            "id");
    User user = mock(User.class);
    UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

    given(delegate.loadUser(request)).willReturn(oauth2User);
    given(userRepository.findByProviderAndProviderId("kakao", "12345"))
        .willReturn(Optional.empty());
    given(userRepository.findByEmail("kakao@example.com")).willReturn(Optional.of(user));
    given(user.getId()).willReturn(userId);
    given(user.getEmail()).willReturn("kakao@example.com");
    given(user.getNickname()).willReturn("기존유저");
    given(user.getProvider()).willReturn("kakao");

    OAuth2User principal = service.loadUser(request);

    verify(user).linkOAuthProvider("kakao", "12345");
    assertThat(principal.getName()).isEqualTo(userId.toString());
    assertThat((String) principal.getAttribute(CustomOAuth2UserService.USER_ID_ATTRIBUTE))
        .isEqualTo(userId.toString());
    assertThat((String) principal.getAttribute("email")).isEqualTo("kakao@example.com");
    assertThat((String) principal.getAttribute("nickname")).isEqualTo("기존유저");
    assertThat((String) principal.getAttribute("provider")).isEqualTo("kakao");
  }

  @Test
  @DisplayName("OAuth2 provider가 이메일을 제공하지 않으면 회원을 생성하지 않고 인증 실패 처리한다")
  void loadUserFailsWhenEmailIsMissing() {
    OAuth2UserRequest request = userRequest("kakao");
    OAuth2User oauth2User =
        new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("id", 12345, "kakao_account", Map.of("profile", Map.of("nickname", "카카오유저"))),
            "id");

    given(delegate.loadUser(request)).willReturn(oauth2User);
    given(userRepository.findByProviderAndProviderId("kakao", "12345"))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> service.loadUser(request))
        .isInstanceOf(OAuth2AuthenticationException.class);
    verify(userRepository, never()).findByEmail("kakao@example.com");
    verify(userRepository, never()).save(any(User.class));
  }

  private OAuth2UserRequest userRequest(String registrationId) {
    ClientRegistration registration =
        ClientRegistration.withRegistrationId(registrationId)
            .clientId("client-id")
            .clientSecret("client-secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/login/oauth2/code/" + registrationId)
            .authorizationUri("https://provider.example/oauth/authorize")
            .tokenUri("https://provider.example/oauth/token")
            .userInfoUri("https://provider.example/userinfo")
            .userNameAttributeName("id")
            .clientName(registrationId)
            .build();
    OAuth2AccessToken accessToken =
        new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token", null, null);
    return new OAuth2UserRequest(registration, accessToken);
  }
}
