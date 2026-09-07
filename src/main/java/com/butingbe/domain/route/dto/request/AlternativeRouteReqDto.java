package com.butingbe.domain.route.dto.request;

import com.butingbe.domain.route.dto.RoutePoint;
import com.butingbe.domain.travel.entity.TransportType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.util.List;
import java.util.UUID;

/**
 * 대체 경로 생성 요청.
 *
 * <p>이동이 어렵거나 들르기 곤란해진 장소를 {@code excludePlaceIds}로 빼면, 남은 장소만으로 경로를 다시 짠다. 출발 좌표를 주면 그 지점에서 시작하므로
 * "지금 여기서부터, 못 가게 된 곳은 빼고 다시" 하는 상황에 맞는다.
 */
public record AlternativeRouteReqDto(
    List<UUID> excludePlaceIds,
    @DecimalMin("-90.0") @DecimalMax("90.0") Double startLatitude,
    @DecimalMin("-180.0") @DecimalMax("180.0") Double startLongitude,
    String startName,
    TransportType transportType) {

  public List<UUID> excludePlaceIdsOrEmpty() {
    return excludePlaceIds == null ? List.of() : excludePlaceIds;
  }

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
