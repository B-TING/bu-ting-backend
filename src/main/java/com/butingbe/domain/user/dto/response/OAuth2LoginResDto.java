package com.butingbe.domain.user.dto.response;

import com.butingbe.domain.user.entity.User;

public record OAuth2LoginResDto(
    String userId,
    String email,
    String nickname,
    String provider,
    boolean loggedIn,
    boolean emailRequired,
    String accessToken,
    String tokenType,
    long expiresIn,
    String refreshToken,
    long refreshExpiresIn) {

  public static OAuth2LoginResDto from(
      User user,
      String accessToken,
      String tokenType,
      long expiresIn,
      String refreshToken,
      long refreshExpiresIn) {
    return new OAuth2LoginResDto(
        user.getId().toString(),
        user.getEmail(),
        user.getNickname(),
        user.getProvider(),
        true,
        false,
        accessToken,
        tokenType,
        expiresIn,
        refreshToken,
        refreshExpiresIn);
  }
}
