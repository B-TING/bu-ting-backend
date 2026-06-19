package com.butingbe.domain.auth.controller;

import com.butingbe.domain.auth.dto.request.OAuthLoginReqDto;
import com.butingbe.domain.auth.service.OAuthLoginService;
import com.butingbe.domain.user.dto.response.OAuth2LoginResDto;
import com.butingbe.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final OAuthLoginService oAuthLoginService;

  @PostMapping("/oauth/login")
  public ResponseEntity<ApiResponse<OAuth2LoginResDto>> loginWithOAuth(
      @RequestBody @Valid OAuthLoginReqDto request) {
    OAuth2LoginResDto response = oAuthLoginService.login(request);
    return ResponseEntity.ok(ApiResponse.success("OAuth login succeeded.", response));
  }
}
