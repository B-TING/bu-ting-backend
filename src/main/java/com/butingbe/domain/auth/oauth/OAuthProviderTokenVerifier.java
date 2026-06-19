package com.butingbe.domain.auth.oauth;

import com.butingbe.domain.user.oauth.OAuth2UserInfo;

public interface OAuthProviderTokenVerifier {

  OAuth2UserInfo verify(
      String provider, String providerToken, String redirectUri, String codeVerifier);
}
