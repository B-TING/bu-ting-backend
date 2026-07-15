package com.butingbe.domain.travelrecord.dto.response;

import com.butingbe.domain.travelrecord.entity.PlaceReview;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PlaceReviewResDto(
    UUID placeReviewId,
    UUID planPlaceId,
    UUID travelRecordPlaceId,
    Integer rating,
    String content,
    List<String> tags,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static PlaceReviewResDto from(PlaceReview placeReview) {
    return new PlaceReviewResDto(
        placeReview.getId(),
        placeReview.getPlanPlace() == null ? null : placeReview.getPlanPlace().getId(),
        placeReview.getTravelRecordPlace() == null ? null : placeReview.getTravelRecordPlace().getId(),
        placeReview.getRating(),
        placeReview.getContent(),
        List.copyOf(placeReview.getTags()),
        placeReview.getCreatedAt(),
        placeReview.getUpdatedAt());
  }
}
