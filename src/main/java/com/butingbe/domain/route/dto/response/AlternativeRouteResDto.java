package com.butingbe.domain.route.dto.response;

import com.butingbe.domain.travel.entity.TransportType;
import java.util.List;
import java.util.UUID;

/**
 * 대체 경로와 기존 경로의 비교.
 *
 * <p>{@code alternative}는 제외한 장소를 뺀 나머지를 다시 최적화한 경로다. {@code originalDurationMinutes}는 아무것도 빼지 않은
 * 일정을 원래 순서대로 이동했을 때의 시간으로, 대체 경로가 얼마나 짧아졌는지 견주는 기준이다.
 *
 * <p>{@code excludedPlaceIds}는 요청으로 뺀 장소, {@code skippedPlaceIds}는 좌표가 없어 계산에서 빠진 장소다. 이유가 다르므로 나눠서
 * 알린다.
 */
public record AlternativeRouteResDto(
    TransportType transportType,
    VisitOrderResDto alternative,
    int alternativeDurationMinutes,
    int originalDurationMinutes,
    int reducedMinutes,
    List<UUID> excludedPlaceIds,
    List<UUID> skippedPlaceIds) {

  public static AlternativeRouteResDto of(
      TransportType transportType,
      VisitOrderResDto alternative,
      int originalDurationMinutes,
      List<UUID> excludedPlaceIds,
      List<UUID> skippedPlaceIds) {
    int alternativeDuration = alternative.totalDurationMinutes();
    return new AlternativeRouteResDto(
        transportType,
        alternative,
        alternativeDuration,
        originalDurationMinutes,
        Math.max(0, originalDurationMinutes - alternativeDuration),
        List.copyOf(excludedPlaceIds),
        List.copyOf(skippedPlaceIds));
  }
}
