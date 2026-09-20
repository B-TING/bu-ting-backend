package com.butingbe.domain.place.service;

import com.butingbe.domain.place.entity.Place;
import com.butingbe.domain.place.entity.PlaceTimeSlot;
import com.butingbe.domain.place.repository.PlaceRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 적재된 장소 카탈로그에서 시간대를 읽는다. 카탈로그에 없거나 지정되지 않았으면 비어 있다. */
@Component
@RequiredArgsConstructor
public class CatalogPlaceTimeSlotProvider implements PlaceTimeSlotProvider {

  private final PlaceRepository placeRepository;

  @Override
  @Transactional(readOnly = true)
  public Optional<PlaceTimeSlot> timeSlot(String provider, String providerPlaceId) {
    return CatalogPlaceLookup.find(placeRepository, provider, providerPlaceId)
        .map(Place::getPreferredTimeSlot);
  }
}
