package com.butingbe.domain.user.oauth;

import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.util.StringUtils;

public final class OAuth2UserInfoFactory {

  private OAuth2UserInfoFactory() {}

  public static OAuth2UserInfo from(String registrationId, Map<String, Object> attributes) {
    return switch (registrationId.toLowerCase()) {
      case "google" -> google(attributes);
      case "naver" -> naver(attributes);
      case "kakao" -> kakao(attributes);
      default ->
          throw new OAuth2AuthenticationException(
              new OAuth2Error("unsupported_oauth2_provider"),
              "Unsupported OAuth2 provider: " + registrationId);
    };
  }

  private static OAuth2UserInfo google(Map<String, Object> attributes) {
    return validated(
        new OAuth2UserInfo(
            "google",
            value(attributes.get("sub")),
            value(attributes.get("email")),
            value(attributes.get("name")),
            value(attributes.get("given_name")),
            value(attributes.get("family_name")),
            attributes));
  }

  @SuppressWarnings("unchecked")
  private static OAuth2UserInfo naver(Map<String, Object> attributes) {
    Map<String, Object> response =
        (Map<String, Object>) attributes.getOrDefault("response", Map.of());
    return validated(
        new OAuth2UserInfo(
            "naver",
            value(response.get("id")),
            value(response.get("email")),
            firstText(response.get("nickname"), response.get("name")),
            value(response.get("name")),
            "naver",
            attributes));
  }

  @SuppressWarnings("unchecked")
  private static OAuth2UserInfo kakao(Map<String, Object> attributes) {
    Map<String, Object> account =
        (Map<String, Object>) attributes.getOrDefault("kakao_account", Map.of());
    Map<String, Object> profile = (Map<String, Object>) account.getOrDefault("profile", Map.of());
    return validated(
        new OAuth2UserInfo(
            "kakao",
            value(attributes.get("id")),
            value(account.get("email")),
            value(profile.get("nickname")),
            value(profile.get("nickname")),
            "kakao",
            attributes));
  }

  private static OAuth2UserInfo validated(OAuth2UserInfo userInfo) {
    if (!StringUtils.hasText(userInfo.providerId()) || !StringUtils.hasText(userInfo.email())) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error("invalid_oauth2_user_info"),
          "OAuth2 provider did not return required user information.");
    }
    return userInfo;
  }

  private static String firstText(Object first, Object second) {
    String firstValue = value(first);
    return StringUtils.hasText(firstValue) ? firstValue : value(second);
  }

  private static String value(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
