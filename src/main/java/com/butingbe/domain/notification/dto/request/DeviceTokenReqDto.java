package com.butingbe.domain.notification.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 디바이스 토큰 등록/갱신. */
public record DeviceTokenReqDto(@NotBlank String fcmToken, @NotBlank String platform) {}
