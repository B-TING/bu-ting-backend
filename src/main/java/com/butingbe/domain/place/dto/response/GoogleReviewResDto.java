package com.butingbe.domain.place.dto.response;

import com.butingbe.domain.place.dto.googleplaces.GooglePlaceDetailsResponse;

public record GoogleReviewResDto(
    Integer rating,
    String text,
    String authorName,
    String relativePublishTimeDescription,
    String publishTime) {

  public static GoogleReviewResDto from(GooglePlaceDetailsResponse.Review review) {
    String text = review.text() == null ? null : review.text().text();
    String authorName =
        review.authorAttribution() == null ? null : review.authorAttribution().displayName();
    return new GoogleReviewResDto(
        review.rating(),
        text,
        authorName,
        review.relativePublishTimeDescription(),
        review.publishTime());
  }
}
