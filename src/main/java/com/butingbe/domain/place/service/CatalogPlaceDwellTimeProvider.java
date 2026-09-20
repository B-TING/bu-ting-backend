package com.butingbe.domain.place.service;

import com.butingbe.domain.place.entity.Place;
import com.butingbe.domain.place.entity.PlaceDwellDefaults;
import com.butingbe.domain.place.repository.PlaceRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 적재된 장소 카탈로그에서 체류 시간을 읽는다. 카탈로그에 없는 장소는 유형 기본값으로 답한다. */
@Component
@RequiredArgsConstructor
public class CatalogPlaceDwellTimeProvider implements PlaceDwellTimeProvider {

  private final PlaceRepository placeRepository;

  @Override
  @Transactional(readOnly = true)
  public int dwellMinutes(String provider, String providerPlaceId) {
    return placeRepository
        .findByProviderAndProviderPlaceId(provider, providerPlaceId)
        .map(Place::getDwellMinutes)
        .filter(Objects::nonNull)
        .orElseGet(() -> PlaceDwellDefaults.forContentType(null));
  }
}
