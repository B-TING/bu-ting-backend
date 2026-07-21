package com.butingbe.domain.travel.dto.response;

import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travel.entity.Plan;
import com.butingbe.domain.travel.entity.PlanPlace;
import com.butingbe.domain.travel.entity.PlanRoute;
import com.butingbe.domain.travel.entity.TransportType;
import com.butingbe.domain.travel.entity.Travel;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TravelPlansResDto(UUID travelId, String title, List<PlanDayResDto> days) {

  public static TravelPlansResDto of(Travel travel, List<PlanDayResDto> days) {
    return new TravelPlansResDto(travel.getId(), travel.getTitle(), days);
  }

  public record PlanDayResDto(
      UUID planId, Integer dayNumber, LocalDate visitDate, List<PlanPlaceResDto> places) {

    public static PlanDayResDto of(
        Plan plan, List<PlanPlace> places, Map<UUID, PlanRoute> routeByFromPlaceId) {
      return new PlanDayResDto(
          plan.getId(),
          plan.getDayNumber(),
          plan.getVisitDate(),
          places.stream()
              .map(place -> PlanPlaceResDto.of(place, routeByFromPlaceId.get(place.getId())))
              .toList());
    }
  }

  public record PlanPlaceResDto(
      UUID planPlaceId,
      Integer sequence,
      String placeName,
      String address,
      Double latitude,
      Double longitude,
      PlaceProvider provider,
      String providerPlaceId,
      Integer durationMinutes,
      String memo,
      LocalTime scheduledTime,
      Boolean visited,
      PlanRouteResDto routeToNext) {

    public static PlanPlaceResDto of(PlanPlace place, PlanRoute routeToNext) {
      return new PlanPlaceResDto(
          place.getId(),
          place.getSequence(),
          place.getPlaceName(),
          place.getAddress(),
          place.getLatitude(),
          place.getLongitude(),
          place.getProvider(),
          place.getProviderPlaceId(),
          place.getDurationMinutes(),
          place.getMemo(),
          place.getScheduledTime(),
          place.getVisited(),
          routeToNext == null ? null : PlanRouteResDto.from(routeToNext));
    }
  }

  public record PlanRouteResDto(
      TransportType transportType,
      Integer durationMinutes,
      Integer distanceMeters,
      PlaceProvider provider) {

    public static PlanRouteResDto from(PlanRoute route) {
      return new PlanRouteResDto(
          route.getTransportType(),
          route.getDurationMinutes(),
          route.getDistanceMeters(),
          route.getProvider());
    }
  }
}
