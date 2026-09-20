package com.butingbe.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 액세스 토큰 재발급 요청. */
public record TokenRefreshReqDto(@NotBlank String refreshToken) {}
