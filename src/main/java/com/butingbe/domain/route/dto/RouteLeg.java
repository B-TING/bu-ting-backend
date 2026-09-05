package com.butingbe.domain.route.dto;

import com.butingbe.domain.travel.entity.TransportType;

/** 두 지점 사이의 한 구간. 거리와 소요 시간은 {@link com.butingbe.domain.route.RouteProvider}가 채운다. */
public record RouteLeg(
    RoutePoint from,
    RoutePoint to,
    TransportType transportType,
    int distanceMeters,
    int durationMinutes) {}
