package com.butingbe.domain.user.oauth;

import com.butingbe.domain.user.entity.Name;
import java.util.Map;
import org.springframework.util.StringUtils;

public record OAuth2UserInfo(
    String provider,
    String providerId,
    String email,
    String nickname,
    String firstName,
    String lastName,
    Map<String, Object> attributes) {

  public Name toName() {
    String safeLastName = StringUtils.hasText(lastName) ? lastName.trim() : provider;
    String safeFirstName = StringUtils.hasText(firstName) ? firstName.trim() : safeNickname();
    return new Name(safeLastName, safeFirstName);
  }

  public String safeNickname() {
    if (StringUtils.hasText(nickname)) {
      return nickname.trim();
    }
    if (StringUtils.hasText(email) && email.contains("@")) {
      return email.substring(0, email.indexOf("@"));
    }
    return provider + "-" + providerId;
  }
}
