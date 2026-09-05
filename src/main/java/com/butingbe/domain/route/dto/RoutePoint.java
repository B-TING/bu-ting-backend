package com.butingbe.domain.route.dto;

import java.util.UUID;

/** 경로 계산의 한 지점. 여행 장소일 수도 있고 사용자의 현재 위치처럼 장소가 아닌 지점일 수도 있다. */
public record RoutePoint(UUID placeId, String name, double latitude, double longitude) {

  public static RoutePoint of(String name, double latitude, double longitude) {
    return new RoutePoint(null, name, latitude, longitude);
  }
}
