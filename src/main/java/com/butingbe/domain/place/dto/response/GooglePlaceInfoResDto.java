package com.butingbe.domain.place.dto.response;

import com.butingbe.domain.place.dto.googleplaces.GooglePlaceDetailsResponse;
import java.util.List;

public record GooglePlaceInfoResDto(
    String placeId,
    Double rating,
    Integer reviewCount,
    String priceLevel,
    List<String> openingHours,
    List<GoogleReviewResDto> reviews) {

  public static GooglePlaceInfoResDto from(GooglePlaceDetailsResponse response) {
    if (response == null) {
      return null;
    }

    List<String> openingHours =
        response.regularOpeningHours() == null
            ? List.of()
            : nullSafe(response.regularOpeningHours().weekdayDescriptions());
    List<GoogleReviewResDto> reviews =
        nullSafe(response.reviews()).stream().map(GoogleReviewResDto::from).toList();

    return new GooglePlaceInfoResDto(
        response.id(),
        response.rating(),
        response.userRatingCount(),
        response.priceLevel(),
        openingHours,
        reviews);
  }

  private static <T> List<T> nullSafe(List<T> values) {
    return values == null ? List.of() : values;
  }
}
