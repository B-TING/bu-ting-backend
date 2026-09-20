package com.butingbe.domain.place.service;

import com.butingbe.domain.place.entity.Place;
import com.butingbe.domain.place.repository.PlaceRepository;
import java.util.Optional;

/**
 * 일정 provider와 카탈로그 provider 차이를 흡수한다.
 *
 * <p>앱/일정은 GOOGLE + 관광 contentId 계약을 쓰고, 카탈로그 적재는 TOUR_API로 저장한다. 조회 시 GOOGLE로 오면 TOUR_API도
 * 함께 본다.
 */
final class CatalogPlaceLookup {
  private CatalogPlaceLookup() {}

  static Optional<Place> find(
      PlaceRepository placeRepository, String provider, String providerPlaceId) {
    if (providerPlaceId == null || providerPlaceId.isBlank()) {
      return Optional.empty();
    }
    Optional<Place> exact =
        placeRepository.findByProviderAndProviderPlaceId(provider, providerPlaceId);
    if (exact.isPresent()) {
      return exact;
    }
    if (provider != null && "GOOGLE".equalsIgnoreCase(provider.trim())) {
      return placeRepository.findByProviderAndProviderPlaceId("TOUR_API", providerPlaceId);
    }
    return Optional.empty();
  }
}
