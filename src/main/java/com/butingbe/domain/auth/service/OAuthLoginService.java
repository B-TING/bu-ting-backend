package com.butingbe.domain.auth.service;

import com.butingbe.domain.auth.dto.request.OAuthLoginReqDto;
import com.butingbe.domain.auth.oauth.OAuthProviderTokenVerifier;
import com.butingbe.domain.user.dto.response.OAuth2LoginResDto;
import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.user.entity.UserRole;
import com.butingbe.domain.user.oauth.OAuth2UserInfo;
import com.butingbe.domain.user.repository.UserRepository;
import com.butingbe.global.error.exception.UnauthenticatedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OAuthLoginService {

  private final OAuthProviderTokenVerifier oAuthProviderTokenVerifier;
  private final UserRepository userRepository;
  private final OpaqueTokenService opaqueTokenService;

  @Transactional
  public OAuth2LoginResDto login(OAuthLoginReqDto request) {
    return login(request, null);
  }

  @Transactional
  public OAuth2LoginResDto login(OAuthLoginReqDto request, String authorization) {
    OAuth2UserInfo userInfo =
        oAuthProviderTokenVerifier.verify(
            request.provider(),
            request.providerToken(),
            request.redirectUri(),
            request.codeVerifier());
    requireEmail(userInfo);
    User user = findOrCreate(userInfo);
    OpaqueTokenService.IssuedOpaqueToken token = opaqueTokenService.issue(user, authorization);

    return OAuth2LoginResDto.from(user, token.accessToken(), token.tokenType(), token.expiresIn());
  }

  private User findOrCreate(OAuth2UserInfo userInfo) {
    return userRepository
        .findByProviderAndProviderId(userInfo.provider(), userInfo.providerId())
        .orElseGet(() -> createOrLinkByEmail(userInfo));
  }

  private User createOrLinkByEmail(OAuth2UserInfo userInfo) {
    return userRepository
        .findByEmail(userInfo.email())
        .map(user -> linkProvider(user, userInfo))
        .orElseGet(() -> userRepository.save(create(userInfo)));
  }

  private void requireEmail(OAuth2UserInfo userInfo) {
    if (!StringUtils.hasText(userInfo.email())) {
      throw new UnauthenticatedException("error.auth.unauthenticated");
    }
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
