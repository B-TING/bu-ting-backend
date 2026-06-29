package com.butingbe.domain.user.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.butingbe.domain.auth.service.OpaqueTokenService;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

class OAuth2AuthenticationSuccessHandlerTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final OpaqueTokenService opaqueTokenService = mock(OpaqueTokenService.class);
  private final OAuth2AuthenticationSuccessHandler handler =
      new OAuth2AuthenticationSuccessHandler(userRepository, opaqueTokenService);

  @Test
  @DisplayName("OAuth2 인증 성공 시 사용자 조회 후 opaque token을 발급하고 JSON 응답을 작성한다")
  void onAuthenticationSuccessWritesOAuthLoginResponse() throws Exception {
    UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440020");
    OAuth2User principal =
        new DefaultOAuth2User(
            java.util.List.of(),
            Map.of(CustomOAuth2UserService.USER_ID_ATTRIBUTE, userId.toString()),
            CustomOAuth2UserService.USER_ID_ATTRIBUTE);
    User user = mock(User.class);

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(user.getId()).willReturn(userId);
    given(user.getEmail()).willReturn("oauth@example.com");
    given(user.getNickname()).willReturn("소셜유저");
    given(user.getProvider()).willReturn("kakao");
    given(opaqueTokenService.issue(user))
        .willReturn(
            new OpaqueTokenService.IssuedOpaqueToken(
                "opaque-token", "Bearer", 3600, LocalDateTime.now().plusHours(1)));

    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.onAuthenticationSuccess(
        new MockHttpServletRequest(), response, new TestingAuthenticationToken(principal, null));

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
    assertThat(response.getContentAsString())
        .contains("\"success\":true")
        .contains("\"userId\":\"" + userId + "\"")
        .contains("\"email\":\"oauth@example.com\"")
        .contains("\"nickname\":\"소셜유저\"")
        .contains("\"provider\":\"kakao\"")
        .contains("\"emailRequired\":false")
        .contains("\"accessToken\":\"opaque-token\"")
        .contains("\"tokenType\":\"Bearer\"")
        .contains("\"expiresIn\":3600");
  }

  @Test
  @DisplayName("OAuth2 인증 성공 응답에서 문자열 값을 JSON escape 처리한다")
  void onAuthenticationSuccessEscapesJsonValues() throws Exception {
    UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440021");
    OAuth2User principal =
        new DefaultOAuth2User(
            java.util.List.of(),
            Map.of(CustomOAuth2UserService.USER_ID_ATTRIBUTE, userId.toString()),
            CustomOAuth2UserService.USER_ID_ATTRIBUTE);
    User user = mock(User.class);

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(user.getId()).willReturn(userId);
    given(user.getEmail()).willReturn("quote@example.com");
    given(user.getNickname()).willReturn("닉\"네\n임");
    given(user.getProvider()).willReturn("ka\\kao");
    given(opaqueTokenService.issue(user))
        .willReturn(
            new OpaqueTokenService.IssuedOpaqueToken(
                "opaque\"token", "Bearer", 3600, LocalDateTime.now().plusHours(1)));

    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.onAuthenticationSuccess(
        new MockHttpServletRequest(), response, new TestingAuthenticationToken(principal, null));

    assertThat(response.getContentAsString())
        .contains("\"nickname\":\"닉\\\"네\\n임\"")
        .contains("\"provider\":\"ka\\\\kao\"")
        .contains("\"accessToken\":\"opaque\\\"token\"");
  }
}
