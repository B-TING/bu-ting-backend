package com.butingbe.domain.notification.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 운영 즉시 푸시. targetType은 ZONE_TOPIC(zoneId 필요) 또는 ALL. */
public record AdminPushReqDto(
    @NotBlank String targetType, String zoneId, @NotBlank String title, @NotBlank String body) {}
