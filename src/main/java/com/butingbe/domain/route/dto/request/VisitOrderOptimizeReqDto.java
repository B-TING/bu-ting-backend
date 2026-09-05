package com.butingbe.domain.route.dto.request;

import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.travel.entity.TransportType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * 방문 순서 최적화 요청.
 *
 * <p>출발 좌표를 주면 그 지점에서 시작하는 경로를 계산한다. 현재 위치에서 남은 일정을 다시 짜는 경우에 쓴다. 주지 않으면 일정의 첫 장소에서 시작한다.
 */
public record VisitOrderOptimizeReqDto(
    @DecimalMin("-90.0") @DecimalMax("90.0") Double startLatitude,
    @DecimalMin("-180.0") @DecimalMax("180.0") Double startLongitude,
    String startName,
    TransportType transportType) {

  /** 출발 좌표가 모두 주어졌을 때만 출발 지점을 만든다. */
  public RoutePoint startPointOrNull() {
    if (startLatitude == null || startLongitude == null) {
      return null;
    }
    return RoutePoint.of(
        startName == null || startName.isBlank() ? "출발 위치" : startName,
        startLatitude,
        startLongitude);
  }
}
