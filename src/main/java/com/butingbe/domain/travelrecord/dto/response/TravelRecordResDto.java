package com.butingbe.domain.travelrecord.dto.response;

import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travel.entity.TransportType;
import com.butingbe.domain.travelrecord.entity.TravelRecord;
import com.butingbe.domain.travelrecord.entity.TravelRecordDay;
import com.butingbe.domain.travelrecord.entity.TravelRecordPlace;
import com.butingbe.domain.travelrecord.entity.TravelRecordRoute;
import com.butingbe.domain.travelrecord.entity.TravelRecordStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TravelRecordResDto(
    UUID travelRecordId,
    UUID originalTravelId,
    UUID authorId,
    String title,
    String content,
    String coverImageUrl,
    LocalDate travelStartDate,
    LocalDate travelEndDate,
    TravelRecordStatus status,
    LocalDateTime publishedAt,
    long likeCount,
    long viewCount,
    List<TravelRecordDayResDto> days) {

  public static TravelRecordResDto of(TravelRecord travelRecord, List<TravelRecordDayResDto> days) {
    return new TravelRecordResDto(
        travelRecord.getId(),
        travelRecord.getOriginalTravel() == null ? null : travelRecord.getOriginalTravel().getId(),
        travelRecord.getAuthor().getId(),
        travelRecord.getTitle(),
        travelRecord.getContent(),
        travelRecord.getCoverImageUrl(),
        travelRecord.getTravelStartDate(),
        travelRecord.getTravelEndDate(),
        travelRecord.getStatus(),
        travelRecord.getPublishedAt(),
        travelRecord.getLikeCount(),
        travelRecord.getViewCount(),
        days);
  }

  public record TravelRecordDayResDto(
      UUID travelRecordDayId,
      UUID originalPlanId,
      Integer dayNumber,
      LocalDate visitDate,
      List<TravelRecordPlaceResDto> places) {

    public static TravelRecordDayResDto of(
        TravelRecordDay day,
        List<TravelRecordPlace> places,
        Map<UUID, TravelRecordRoute> routeByFromPlaceId) {
      return new TravelRecordDayResDto(
          day.getId(),
          day.getOriginalPlanId(),
          day.getDayNumber(),
          day.getVisitDate(),
          places.stream()
              .map(place -> TravelRecordPlaceResDto.of(place, routeByFromPlaceId.get(place.getId())))
              .toList());
    }
  }

  public record TravelRecordPlaceResDto(
      UUID travelRecordPlaceId,
      UUID originalPlanPlaceId,
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
      TravelRecordRouteResDto routeToNext) {

    public static TravelRecordPlaceResDto of(
        TravelRecordPlace place, TravelRecordRoute routeToNext) {
      return new TravelRecordPlaceResDto(
          place.getId(),
          place.getOriginalPlanPlaceId(),
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
          routeToNext == null ? null : TravelRecordRouteResDto.from(routeToNext));
    }
  }

  public record TravelRecordRouteResDto(
      TransportType transportType,
      Integer durationMinutes,
      Integer distanceMeters,
      PlaceProvider provider) {

    public static TravelRecordRouteResDto from(TravelRecordRoute route) {
      return new TravelRecordRouteResDto(
          route.getTransportType(),
          route.getDurationMinutes(),
          route.getDistanceMeters(),
          route.getProvider());
    }
  }
}
