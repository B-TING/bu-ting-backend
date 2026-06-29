package com.butingbe.domain.auth.oauth;

import com.butingbe.domain.user.oauth.OAuth2UserInfo;
import com.butingbe.domain.user.oauth.OAuth2UserInfoFactory;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class RestClientOAuthProviderTokenVerifier implements OAuthProviderTokenVerifier {

  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};
  private static final String BEARER_PREFIX = "Bearer ";

  private final RestClient restClient;
  private final String googleClientId;
  private final String googleClientSecret;
  private final String googleRedirectUri;
  private final String naverClientId;
  private final String naverClientSecret;
  private final String naverRedirectUri;
  private final String kakaoClientId;
  private final String kakaoClientSecret;
  private final String kakaoRedirectUri;

  @Autowired
  public RestClientOAuthProviderTokenVerifier(
      @Value("${oauth.google.client-id:}") String googleClientId,
      @Value("${oauth.google.client-secret:}") String googleClientSecret,
      @Value("${oauth.google.redirect-uri:}") String googleRedirectUri,
      @Value("${oauth.naver.client-id:}") String naverClientId,
      @Value("${oauth.naver.client-secret:}") String naverClientSecret,
      @Value("${oauth.naver.redirect-uri:}") String naverRedirectUri,
      @Value("${oauth.kakao.client-id:}") String kakaoClientId,
      @Value("${oauth.kakao.client-secret:}") String kakaoClientSecret,
      @Value("${oauth.kakao.redirect-uri:}") String kakaoRedirectUri) {
    this(
        RestClient.create(),
        googleClientId,
        googleClientSecret,
        googleRedirectUri,
        naverClientId,
        naverClientSecret,
        naverRedirectUri,
        kakaoClientId,
        kakaoClientSecret,
        kakaoRedirectUri);
  }

  RestClientOAuthProviderTokenVerifier(
      RestClient restClient,
      String googleClientId,
      String googleClientSecret,
      String googleRedirectUri,
      String naverClientId,
      String naverClientSecret,
      String naverRedirectUri,
      String kakaoClientId,
      String kakaoClientSecret,
      String kakaoRedirectUri) {
    this.restClient = restClient;
    this.googleClientId = googleClientId;
    this.googleClientSecret = googleClientSecret;
    this.googleRedirectUri = googleRedirectUri;
    this.naverClientId = naverClientId;
    this.naverClientSecret = naverClientSecret;
    this.naverRedirectUri = naverRedirectUri;
    this.kakaoClientId = kakaoClientId;
    this.kakaoClientSecret = kakaoClientSecret;
    this.kakaoRedirectUri = kakaoRedirectUri;
  }

  @Override
  public OAuth2UserInfo verify(
      String provider, String providerToken, String redirectUri, String codeVerifier) {
    if (!StringUtils.hasText(provider) || !StringUtils.hasText(providerToken)) {
      throw new UnauthenticatedException("error.auth.unauthenticated");
    }

    String normalizedProviderToken = normalizeProviderToken(providerToken);
    try {
      return switch (provider.toLowerCase()) {
        case "google" -> verifyGoogle(normalizedProviderToken, redirectUri, codeVerifier);
        case "naver" -> verifyNaver(normalizedProviderToken, redirectUri, codeVerifier);
        case "kakao" -> verifyKakao(normalizedProviderToken, redirectUri, codeVerifier);
        default -> throw new UnauthenticatedException("error.auth.unauthenticated");
      };
    } catch (RuntimeException e) {
      throw new UnauthenticatedException("error.auth.unauthenticated");
    }
  }

  private String normalizeProviderToken(String providerToken) {
    String token = providerToken.trim();
    return token.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
        ? token.substring(BEARER_PREFIX.length()).trim()
        : token;
  }

  private OAuth2UserInfo verifyGoogle(
      String authorizationCode, String redirectUri, String codeVerifier) {
    if (!hasGoogleCodeExchangeConfig(redirectUri)) {
      throw new UnauthenticatedException("error.auth.unauthenticated");
    }

    return verifyGoogleTokenResponse(
        exchangeGoogleAuthorizationCode(authorizationCode, redirectUri, codeVerifier));
  }

  private OAuth2UserInfo fetchGoogleIdTokenInfo(String idToken) {
    Map<String, Object> attributes =
        restClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .scheme("https")
                        .host("oauth2.googleapis.com")
                        .path("/tokeninfo")
                        .queryParam("id_token", idToken)
                        .build())
            .retrieve()
            .body(MAP_TYPE);
    return OAuth2UserInfoFactory.from("google", attributes);
  }

  private OAuth2UserInfo verifyNaver(
      String authorizationCode, String redirectUri, String codeVerifier) {
    if (!hasNaverCodeExchangeConfig()) {
      throw new UnauthenticatedException("error.auth.unauthenticated");
    }

    return fetchNaverUserInfo(
        exchangeNaverAuthorizationCode(authorizationCode, redirectUri, codeVerifier));
  }

  private OAuth2UserInfo fetchNaverUserInfo(String accessToken) {
    Map<String, Object> attributes =
        restClient
            .get()
            .uri("https://openapi.naver.com/v1/nid/me")
            .headers(headers -> headers.setBearerAuth(accessToken))
            .retrieve()
            .body(MAP_TYPE);
    return OAuth2UserInfoFactory.from("naver", attributes);
  }

  private OAuth2UserInfo verifyGoogleTokenResponse(Map<String, Object> tokenResponse) {
    String idToken = value(tokenResponse == null ? null : tokenResponse.get("id_token"));
    if (StringUtils.hasText(idToken)) {
      return fetchGoogleIdTokenInfo(idToken);
    }

    String accessToken = value(tokenResponse == null ? null : tokenResponse.get("access_token"));
    if (!StringUtils.hasText(accessToken)) {
      throw new UnauthenticatedException("error.auth.unauthenticated");
    }
    return fetchGoogleUserInfo(accessToken);
  }

  private OAuth2UserInfo fetchGoogleUserInfo(String accessToken) {
    Map<String, Object> attributes =
        restClient
            .get()
            .uri("https://openidconnect.googleapis.com/v1/userinfo")
            .headers(headers -> headers.setBearerAuth(accessToken))
            .retrieve()
            .body(MAP_TYPE);
    return OAuth2UserInfoFactory.from("google", attributes);
  }

  private OAuth2UserInfo verifyKakao(
      String authorizationCode, String redirectUri, String codeVerifier) {
    if (!hasKakaoCodeExchangeConfig(redirectUri)) {
      throw new UnauthenticatedException("error.auth.unauthenticated");
    }

    return fetchKakaoUserInfo(
        exchangeKakaoAuthorizationCode(authorizationCode, redirectUri, codeVerifier));
  }

  private OAuth2UserInfo fetchKakaoUserInfo(String accessToken) {
    Map<String, Object> attributes =
        restClient
            .get()
            .uri("https://kapi.kakao.com/v2/user/me")
            .headers(headers -> headers.setBearerAuth(accessToken))
            .retrieve()
            .body(MAP_TYPE);
    return OAuth2UserInfoFactory.from("kakao", attributes);
  }

  private Map<String, Object> exchangeGoogleAuthorizationCode(
      String authorizationCode, String redirectUri, String codeVerifier) {
    return restClient
        .post()
        .uri("https://oauth2.googleapis.com/token")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(
            authorizationCodeRequestBody(
                googleClientId,
                googleClientSecret,
                effectiveRedirectUri(redirectUri, googleRedirectUri),
                codePart(authorizationCode),
                codeVerifier))
        .retrieve()
        .body(MAP_TYPE);
  }

  private String exchangeNaverAuthorizationCode(
      String providerToken, String redirectUri, String codeVerifier) {
    AuthorizationCodeParts authorizationCodeParts = authorizationCodeParts(providerToken);
    Map<String, Object> tokenResponse =
        restClient
            .post()
            .uri("https://nid.naver.com/oauth2.0/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(naverTokenRequestBody(authorizationCodeParts, redirectUri, codeVerifier))
            .retrieve()
            .body(MAP_TYPE);

    String accessToken = value(tokenResponse == null ? null : tokenResponse.get("access_token"));
    if (!StringUtils.hasText(accessToken)) {
      throw new UnauthenticatedException("error.auth.unauthenticated");
    }
    return accessToken;
  }

  private String exchangeKakaoAuthorizationCode(
      String authorizationCode, String redirectUri, String codeVerifier) {
    Map<String, Object> tokenResponse =
        restClient
            .post()
            .uri("https://kauth.kakao.com/oauth/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                authorizationCodeRequestBody(
                    kakaoClientId,
                    kakaoClientSecret,
                    effectiveRedirectUri(redirectUri, kakaoRedirectUri),
                    codePart(authorizationCode),
                    codeVerifier))
            .retrieve()
            .body(MAP_TYPE);

    String accessToken = value(tokenResponse == null ? null : tokenResponse.get("access_token"));
    if (!StringUtils.hasText(accessToken)) {
      throw new UnauthenticatedException("error.auth.unauthenticated");
    }
    return accessToken;
  }

  private String authorizationCodeRequestBody(
      String clientId,
      String clientSecret,
      String redirectUri,
      String authorizationCode,
      String codeVerifier) {
    StringBuilder body =
        new StringBuilder()
            .append("grant_type=authorization_code")
            .append("&client_id=")
            .append(formValue(clientId))
            .append("&redirect_uri=")
            .append(formValue(redirectUri))
            .append("&code=")
            .append(formValue(authorizationCode));

    if (StringUtils.hasText(clientSecret)) {
      body.append("&client_secret=").append(formValue(clientSecret));
    }

    if (StringUtils.hasText(codeVerifier)) {
      body.append("&code_verifier=").append(formValue(codeVerifier));
    }

    return body.toString();
  }

  private String naverTokenRequestBody(
      AuthorizationCodeParts authorizationCodeParts, String redirectUri, String codeVerifier) {
    StringBuilder body =
        new StringBuilder()
            .append("grant_type=authorization_code")
            .append("&client_id=")
            .append(formValue(naverClientId))
            .append("&client_secret=")
            .append(formValue(naverClientSecret))
            .append("&code=")
            .append(formValue(authorizationCodeParts.code()));

    String effectiveRedirectUri = effectiveRedirectUri(redirectUri, naverRedirectUri);
    if (StringUtils.hasText(effectiveRedirectUri)) {
      body.append("&redirect_uri=").append(formValue(effectiveRedirectUri));
    }

    if (StringUtils.hasText(authorizationCodeParts.state())) {
      body.append("&state=").append(formValue(authorizationCodeParts.state()));
    }

    if (StringUtils.hasText(codeVerifier)) {
      body.append("&code_verifier=").append(formValue(codeVerifier));
    }

    return body.toString();
  }

  private boolean hasKakaoCodeExchangeConfig(String redirectUri) {
    return StringUtils.hasText(kakaoClientId)
        && StringUtils.hasText(effectiveRedirectUri(redirectUri, kakaoRedirectUri));
  }

  private boolean hasGoogleCodeExchangeConfig(String redirectUri) {
    return StringUtils.hasText(googleClientId)
        && StringUtils.hasText(effectiveRedirectUri(redirectUri, googleRedirectUri));
  }

  private boolean hasNaverCodeExchangeConfig() {
    return StringUtils.hasText(naverClientId) && StringUtils.hasText(naverClientSecret);
  }

  private String codePart(String providerToken) {
    return authorizationCodeParts(providerToken).code();
  }

  private AuthorizationCodeParts authorizationCodeParts(String providerToken) {
    if (!providerToken.contains("=")) {
      return new AuthorizationCodeParts(providerToken, null);
    }

    String query =
        providerToken.contains("?")
            ? providerToken.substring(providerToken.indexOf('?') + 1)
            : providerToken;
    var params =
        UriComponentsBuilder.fromUriString("http://localhost/?" + query).build().getQueryParams();
    String code = params.getFirst("code");
    if (!StringUtils.hasText(code)) {
      return new AuthorizationCodeParts(providerToken, null);
    }
    return new AuthorizationCodeParts(code, params.getFirst("state"));
  }

  private String value(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private String formValue(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String effectiveRedirectUri(String requestRedirectUri, String configuredRedirectUri) {
    return StringUtils.hasText(requestRedirectUri) ? requestRedirectUri : configuredRedirectUri;
  }

  private record AuthorizationCodeParts(String code, String state) {}
}
