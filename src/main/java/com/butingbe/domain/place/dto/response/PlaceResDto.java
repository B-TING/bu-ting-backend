package com.butingbe.domain.place.dto.response;

import com.butingbe.domain.place.dto.tourapi.TourPlaceItem;

public record PlaceResDto(
    String contentId,
    String contentTypeId,
    String title,
    String address,
    String imageUrl,
    String thumbnailUrl,
    Double longitude,
    Double latitude,
    String regionCode,
    String districtCode) {

  public static PlaceResDto from(TourPlaceItem item) {
    return new PlaceResDto(
        item.contentid(),
        item.contenttypeid(),
        item.title(),
        item.addr1(),
        item.firstimage(),
        item.firstimage2(),
        doubleOrNull(item.mapx()),
        doubleOrNull(item.mapy()),
        item.lDongRegnCd(),
        item.lDongSignguCd());
  }

  private static Double doubleOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    try {
      return Double.valueOf(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
