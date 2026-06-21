package com.butingbe.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OAuthLoginReqDto(
    @NotBlank(message = "{validation.auth.provider.required}") String provider,
    @NotBlank(message = "{validation.auth.provider_token.required}") String providerToken,
    String redirectUri,
    String codeVerifier) {}
