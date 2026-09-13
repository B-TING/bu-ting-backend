package com.butingbe.domain.route.dto.request;

import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.travel.entity.TransportType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 여행 리부트 요청.
 *
 * <p>계획에서 벗어난 사용자가 지금 있는 위치와 앞으로 쓸 수 있는 시간을 준다. 서버는 아직 안 간 장소를 현재 위치에서 최적 순서로 다시 짜되, 남은 시간에 담기는 만큼만
 * 제안한다.
 */
public record TravelRebootReqDto(
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double currentLatitude,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double currentLongitude,
    String currentName,
    @NotNull @Positive Integer availableMinutes,
    TransportType transportType) {

  public RoutePoint currentPoint() {
    return RoutePoint.of(
        currentName == null || currentName.isBlank() ? "현재 위치" : currentName,
        currentLatitude,
        currentLongitude);
  }
}
