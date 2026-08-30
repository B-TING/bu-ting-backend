package com.butingbe.domain.travel.ai;

import com.butingbe.domain.travel.entity.PlaceProvider;
import java.util.Locale;

public record PlaceKey(PlaceProvider provider, String providerPlaceId) {
  public static PlaceKey of(String provider, String providerPlaceId) {
    if (provider == null
        || providerPlaceId == null
        || providerPlaceId.isBlank()
        || providerPlaceId.length() > 255
        || !providerPlaceId.equals(providerPlaceId.trim())) {
      throw new IllegalArgumentException("Invalid place reference");
    }
    return new PlaceKey(
        PlaceProvider.valueOf(provider.trim().toUpperCase(Locale.ROOT)), providerPlaceId);
  }
}
