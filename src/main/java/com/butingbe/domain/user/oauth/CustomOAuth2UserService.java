package com.butingbe.domain.user.oauth;

import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

  public static final String USER_ID_ATTRIBUTE = "butingUserId";

  private final UserRepository userRepository;
  private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

  @Autowired
  public CustomOAuth2UserService(UserRepository userRepository) {
    this(userRepository, new DefaultOAuth2UserService());
  }

  CustomOAuth2UserService(
      UserRepository userRepository, OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate) {
    this.userRepository = userRepository;
    this.delegate = delegate;
  }

  @Override
  @Transactional
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User oauth2User = delegate.loadUser(userRequest);
    String registrationId = userRequest.getClientRegistration().getRegistrationId();
    OAuth2UserInfo userInfo =
        OAuth2UserInfoFactory.from(registrationId, oauth2User.getAttributes());
    User user = saveOrUpdate(userInfo);

    Map<String, Object> attributes = new LinkedHashMap<>(userInfo.attributes());
    attributes.put(USER_ID_ATTRIBUTE, user.getId().toString());
    attributes.put("email", user.getEmail());
    attributes.put("nickname", user.getNickname());
    attributes.put("provider", user.getProvider());

    return new DefaultOAuth2User(oauth2User.getAuthorities(), attributes, USER_ID_ATTRIBUTE);
  }

  private User saveOrUpdate(OAuth2UserInfo userInfo) {
    return userRepository
        .findByProviderAndProviderId(userInfo.provider(), userInfo.providerId())
        .orElseGet(() -> createOrLinkByEmail(userInfo));
  }

  private User createOrLinkByEmail(OAuth2UserInfo userInfo) {
    if (!StringUtils.hasText(userInfo.email())) {
      throw new UnauthenticatedException("error.auth.unauthenticated");
    }

    return userRepository
        .findByEmail(userInfo.email())
        .map(user -> linkProvider(user, userInfo))
        .orElseGet(() -> userRepository.save(create(userInfo)));
  }

  private User linkProvider(User user, OAuth2UserInfo userInfo) {
    user.linkOAuthProvider(userInfo.provider(), userInfo.providerId());
    return user;
  }

  private User create(OAuth2UserInfo userInfo) {
    return User.builder()
        .email(userInfo.email())
        .provider(userInfo.provider())
        .providerId(userInfo.providerId())
        .name(userInfo.toName())
        .nickname(userInfo.safeNickname())
        .role(UserRole.USER)
        .build();
  }
}
