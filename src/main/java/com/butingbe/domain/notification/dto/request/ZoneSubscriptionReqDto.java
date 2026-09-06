package com.butingbe.domain.notification.dto.request;

import java.util.List;

/** 관심 구역 전체 교체. */
public record ZoneSubscriptionReqDto(List<String> zoneIds) {}
