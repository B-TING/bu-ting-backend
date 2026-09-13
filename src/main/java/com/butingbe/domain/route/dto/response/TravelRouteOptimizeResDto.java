package com.butingbe.domain.route.dto.response;

import com.butingbe.domain.travel.entity.TransportType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 여행 전체의 일자별 방문 순서 최적화 결과.
 *
 * <p>일자별로 따로 최적화한다. 하루가 끝나면 숙소로 돌아가고 다음 날 다시 나서므로, 여행 전체를 하나의 경로로 묶어 푸는 것은 실제 이동과 맞지 않는다.
 */
public record TravelRouteOptimizeResDto(
    UUID travelId,
    TransportType transportType,
    List<DayRoute> days,
    int totalDurationMinutes,
    int originalDurationMinutes,
    int savedMinutes) {

  public static TravelRouteOptimizeResDto of(
      UUID travelId, TransportType transportType, List<DayRoute> days) {
    int optimized = days.stream().mapToInt(DayRoute::totalDurationMinutes).sum();
    int original = days.stream().mapToInt(DayRoute::originalDurationMinutes).sum();
    return new TravelRouteOptimizeResDto(
        travelId,
        transportType,
        List.copyOf(days),
        optimized,
        original,
        Math.max(0, original - optimized));
  }

  /** 하루치 최적화 결과. */
  public record DayRoute(
      UUID planId,
      Integer dayNumber,
      LocalDate visitDate,
      VisitOrderResDto route,
      int totalDurationMinutes,
      int originalDurationMinutes,
      int savedMinutes) {

    public static DayRoute of(
        UUID planId, Integer dayNumber, LocalDate visitDate, VisitOrderResDto route) {
      return new DayRoute(
          planId,
          dayNumber,
          visitDate,
          route,
          route.totalDurationMinutes(),
          route.originalDurationMinutes(),
          route.savedMinutes());
    }
  }
}
