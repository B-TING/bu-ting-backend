package com.butingbe.domain.notification.dto.response;

import java.util.Map;

/** 알림 유형별 수신 여부. */
public record NotificationSettingsResDto(Map<String, Boolean> settings) {}
