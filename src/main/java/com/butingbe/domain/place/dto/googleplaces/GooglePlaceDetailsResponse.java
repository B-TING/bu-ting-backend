package com.butingbe.domain.place.dto.googleplaces;

import java.util.List;

public record GooglePlaceDetailsResponse(
    String id,
    Double rating,
    Integer userRatingCount,
    String priceLevel,
    OpeningHours regularOpeningHours,
    List<Review> reviews) {

  public record OpeningHours(List<String> weekdayDescriptions) {}

  public record Review(
      Integer rating,
      LocalizedText text,
      AuthorAttribution authorAttribution,
      String relativePublishTimeDescription,
      String publishTime) {}

  public record LocalizedText(String text, String languageCode) {}

  public record AuthorAttribution(String displayName, String uri, String photoUri) {}
}
