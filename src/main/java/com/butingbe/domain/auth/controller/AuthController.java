package com.butingbe.domain.auth.controller;

import com.butingbe.domain.auth.dto.request.OAuthLoginReqDto;
import com.butingbe.domain.auth.dto.request.TokenRefreshReqDto;
import com.butingbe.domain.auth.dto.response.TokenRefreshResDto;
import com.butingbe.domain.auth.service.OAuthLoginService;
import com.butingbe.domain.auth.service.OpaqueTokenService;
import com.butingbe.domain.user.dto.response.OAuth2LoginResDto;
import com.butingbe.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final OAuthLoginService oAuthLoginService;
  private final OpaqueTokenService opaqueTokenService;

  @PostMapping("/oauth/login")
  public ResponseEntity<ApiResponse<OAuth2LoginResDto>> loginWithOAuth(
      @RequestBody @Valid OAuthLoginReqDto request,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
    OAuth2LoginResDto response = oAuthLoginService.login(request, authorization);
    return ResponseEntity.ok(ApiResponse.success("OAuth login succeeded.", response));
  }

  /** 리프레시 토큰으로 액세스 토큰을 다시 받는다. 리프레시도 함께 갈린다. */
  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<TokenRefreshResDto>> refresh(
      @RequestBody @Valid TokenRefreshReqDto request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            "Token refreshed.",
            TokenRefreshResDto.from(opaqueTokenService.refresh(request.refreshToken()))));
  }
}
