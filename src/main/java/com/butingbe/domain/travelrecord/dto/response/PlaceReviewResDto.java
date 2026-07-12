package com.butingbe.domain.travelrecord.dto.response;

import com.butingbe.domain.travelrecord.entity.PlaceReview;
import java.time.LocalDateTime;
import java.util.UUID;

public record PlaceReviewResDto(
    UUID placeReviewId,
    UUID travelRecordPlaceId,
    Integer rating,
    String content,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static PlaceReviewResDto from(PlaceReview placeReview) {
    return new PlaceReviewResDto(
        placeReview.getId(),
        placeReview.getTravelRecordPlace().getId(),
        placeReview.getRating(),
        placeReview.getContent(),
        placeReview.getCreatedAt(),
        placeReview.getUpdatedAt());
  }
}
