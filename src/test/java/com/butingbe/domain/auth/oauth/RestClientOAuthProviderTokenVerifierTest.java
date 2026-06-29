package com.butingbe.domain.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.butingbe.domain.user.oauth.OAuth2UserInfo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientOAuthProviderTokenVerifierTest {

  @Test
  @DisplayName("PKCE S256 code challenge를 code verifier에서 생성한다")
  void createPkceS256CodeChallenge() {
    String codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

    String codeChallenge = codeChallengeS256(codeVerifier);

    assertThat(codeChallenge).isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
  }

  @Test
  @DisplayName("Kakao provider token이 authorization code면 access token으로 교환 후 사용자 정보를 조회한다")
  void verifyKakaoWithAuthorizationCode() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    RestClientOAuthProviderTokenVerifier verifier =
        new RestClientOAuthProviderTokenVerifier(
            builder.build(),
            "",
            "",
            "",
            "",
            "",
            "",
            "KAKAO_REST_API_KEY",
            "KAKAO_CLIENT_SECRET",
            "http://localhost:3000/oauth/kakao/callback");

    server
        .expect(requestTo("https://kauth.kakao.com/oauth/token"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            content()
                .string(
                    "grant_type=authorization_code"
                        + "&client_id=KAKAO_REST_API_KEY"
                        + "&redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Foauth%2Fkakao%2Fcallback"
                        + "&code=KAKAO_AUTHORIZATION_CODE"
                        + "&client_secret=KAKAO_CLIENT_SECRET"
                        + "&code_verifier=KAKAO_CODE_VERIFIER"))
        .andRespond(
            withSuccess(
                """
                {
                  "token_type": "bearer",
                  "access_token": "KAKAO_ACCESS_TOKEN"
                }
                """,
                MediaType.APPLICATION_JSON));

    server
        .expect(requestTo("https://kapi.kakao.com/v2/user/me"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer KAKAO_ACCESS_TOKEN"))
        .andRespond(
            withSuccess(
                """
                {
                  "id": 12345,
                  "kakao_account": {
                    "email": "kakao@example.com",
                    "profile": {
                      "nickname": "카카오유저"
                    }
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    OAuth2UserInfo userInfo =
        verifier.verify("kakao", "KAKAO_AUTHORIZATION_CODE", null, "KAKAO_CODE_VERIFIER");

    assertThat(userInfo.provider()).isEqualTo("kakao");
    assertThat(userInfo.providerId()).isEqualTo("12345");
    assertThat(userInfo.email()).isEqualTo("kakao@example.com");
    server.verify();
  }

  @Test
  @DisplayName("Google provider token은 authorization code로 받아 token endpoint 교환 후 사용자 정보를 조회한다")
  void verifyGoogleWithAuthorizationCode() {
    String codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    String codeChallenge = codeChallengeS256(codeVerifier);
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    RestClientOAuthProviderTokenVerifier verifier =
        new RestClientOAuthProviderTokenVerifier(
            builder.build(),
            "GOOGLE_CLIENT_ID",
            "GOOGLE_CLIENT_SECRET",
            "http://localhost:3000/oauth/google/callback",
            "",
            "",
            "",
            "",
            "",
            "");

    assertThat(codeChallenge).isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");

    server
        .expect(requestTo("https://oauth2.googleapis.com/token"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            content()
                .string(
                    "grant_type=authorization_code"
                        + "&client_id=GOOGLE_CLIENT_ID"
                        + "&redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Foauth%2Fgoogle%2Fcallback"
                        + "&code=GOOGLE_AUTHORIZATION_CODE"
                        + "&client_secret=GOOGLE_CLIENT_SECRET"
                        + "&code_verifier="
                        + codeVerifier))
        .andRespond(
            withSuccess(
                """
                {
                  "access_token": "GOOGLE_ACCESS_TOKEN"
                }
                """,
                MediaType.APPLICATION_JSON));

    server
        .expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer GOOGLE_ACCESS_TOKEN"))
        .andRespond(
            withSuccess(
                """
                {
                  "sub": "google-123",
                  "email": "google@example.com",
                  "name": "Google User"
                }
                """,
                MediaType.APPLICATION_JSON));

    OAuth2UserInfo userInfo =
        verifier.verify("google", "GOOGLE_AUTHORIZATION_CODE", null, codeVerifier);

    assertThat(userInfo.provider()).isEqualTo("google");
    assertThat(userInfo.providerId()).isEqualTo("google-123");
    assertThat(userInfo.email()).isEqualTo("google@example.com");
    server.verify();
  }

  @Test
  @DisplayName("Naver provider token이 authorization code와 state면 access token으로 교환 후 사용자 정보를 조회한다")
  void verifyNaverWithAuthorizationCodeAndState() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    RestClientOAuthProviderTokenVerifier verifier =
        new RestClientOAuthProviderTokenVerifier(
            builder.build(),
            "",
            "",
            "",
            "NAVER_CLIENT_ID",
            "NAVER_CLIENT_SECRET",
            "http://localhost:3000/oauth/naver/callback",
            "",
            "",
            "");

    server
        .expect(requestTo("https://nid.naver.com/oauth2.0/token"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            content()
                .string(
                    "grant_type=authorization_code"
                        + "&client_id=NAVER_CLIENT_ID"
                        + "&client_secret=NAVER_CLIENT_SECRET"
                        + "&code=NAVER_AUTHORIZATION_CODE"
                        + "&redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Foauth%2Fnaver%2Fcallback"
                        + "&state=NAVER_STATE"
                        + "&code_verifier=NAVER_CODE_VERIFIER"))
        .andRespond(
            withSuccess(
                """
                {
                  "access_token": "NAVER_ACCESS_TOKEN"
                }
                """,
                MediaType.APPLICATION_JSON));

    server
        .expect(requestTo("https://openapi.naver.com/v1/nid/me"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer NAVER_ACCESS_TOKEN"))
        .andRespond(
            withSuccess(
                """
                {
                  "response": {
                    "id": "naver-123",
                    "email": "naver@example.com",
                    "nickname": "네이버유저"
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    OAuth2UserInfo userInfo =
        verifier.verify(
            "naver",
            "code=NAVER_AUTHORIZATION_CODE&state=NAVER_STATE",
            null,
            "NAVER_CODE_VERIFIER");

    assertThat(userInfo.provider()).isEqualTo("naver");
    assertThat(userInfo.providerId()).isEqualTo("naver-123");
    assertThat(userInfo.email()).isEqualTo("naver@example.com");
    server.verify();
  }

  private String codeChallengeS256(String codeVerifier) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
