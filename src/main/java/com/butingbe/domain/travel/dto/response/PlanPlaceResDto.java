package com.butingbe.domain.travel.dto.response;

import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travel.entity.PlanPlace;
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
    Integer durationMinutes,
    Boolean visited) {

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
        place.getDurationMinutes(),
        place.getVisited());
  }
}
