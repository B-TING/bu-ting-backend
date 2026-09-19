package com.butingbe.domain.place.dto.response;

import com.butingbe.domain.place.entity.Place;
import com.butingbe.domain.place.entity.PlaceTimeSlot;
import java.util.UUID;

/** 보정 후 장소 상태. */
public record PlaceCurationResDto(
    UUID placeId,
    String name,
    String contentTypeId,
    Integer dwellMinutes,
    PlaceTimeSlot preferredTimeSlot) {

  public static PlaceCurationResDto from(Place place) {
    return new PlaceCurationResDto(
        place.getId(),
        place.getName(),
        place.getContentTypeId(),
        place.getDwellMinutes(),
        place.getPreferredTimeSlot());
  }
}
