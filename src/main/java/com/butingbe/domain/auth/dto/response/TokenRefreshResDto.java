package com.butingbe.domain.auth.dto.response;

import com.butingbe.domain.auth.service.OpaqueTokenService.IssuedOpaqueToken;

/**
 * 재발급 결과.
 *
 * <p>리프레시 토큰도 함께 갈아끼우므로(회전) 클라이언트는 둘 다 저장해야 한다. 이전 리프레시는 이 시점에 폐기돼 다시 쓸 수 없다.
 */
public record TokenRefreshResDto(
    String accessToken,
    String tokenType,
    long expiresIn,
    String refreshToken,
    long refreshExpiresIn) {

  public static TokenRefreshResDto from(IssuedOpaqueToken token) {
    return new TokenRefreshResDto(
        token.accessToken(),
        token.tokenType(),
        token.expiresIn(),
        token.refreshToken(),
        token.refreshExpiresIn());
  }
}
