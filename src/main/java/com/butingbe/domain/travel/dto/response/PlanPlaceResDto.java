package com.butingbe.domain.travel.dto.response;

import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travel.entity.PlanPlace;
import com.butingbe.domain.travel.entity.PlanPlaceSource;
import java.time.LocalTime;
import java.util.UUID;

public record PlanPlaceResDto(
    UUID planPlaceId,
    UUID planId,
    Integer sequence,
    String placeName,
    String address,
    Double latitude,
    Double longitude,
    PlaceProvider provider,
    String providerPlaceId,
    String contentTypeId,
    Integer durationMinutes,
    String memo,
    LocalTime scheduledTime,
    Boolean visited,
    PlanPlaceSource source) {

  public static PlanPlaceResDto from(PlanPlace place) {
    return new PlanPlaceResDto(
        place.getId(),
        place.getPlan().getId(),
        place.getSequence(),
        place.getPlaceName(),
        place.getAddress(),
        place.getLatitude(),
        place.getLongitude(),
        place.getProvider(),
        place.getProviderPlaceId(),
        place.getContentTypeId(),
        place.getDurationMinutes(),
        place.getMemo(),
        place.getScheduledTime(),
        place.getVisited(),
        place.getSource());
  }
}
