package com.butingbe.domain.place.dto.response;

import com.butingbe.domain.place.entity.Place;

/** 일정 후보로 쓰일 장소. 배치에 필요한 최소 정보만 담는다. */
public record PlaceCandidateResDto(
    String provider,
    String providerPlaceId,
    String name,
    String address,
    Double latitude,
    Double longitude,
    String contentTypeId) {

  public static PlaceCandidateResDto from(Place place) {
    return new PlaceCandidateResDto(
        place.getProvider(),
        place.getProviderPlaceId(),
        place.getName(),
        // 주소가 비어 있는 장소도 있어 이름으로 대신한다. 배치에는 좌표만 쓰이고 주소는 표시용이다.
        place.getAddress() == null || place.getAddress().isBlank()
            ? place.getName()
            : place.getAddress(),
        place.getLatitude(),
        place.getLongitude(),
        place.getContentTypeId());
  }
}
