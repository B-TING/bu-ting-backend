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
    Integer stayMinutes,
    String content,
    List<String> tags,
    List<String> mediaUrls,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static PlaceReviewResDto from(PlaceReview placeReview) {
    return from(placeReview, List.of());
  }

  public static PlaceReviewResDto from(PlaceReview placeReview, List<String> mediaUrls) {
    return new PlaceReviewResDto(
        placeReview.getId(),
        placeReview.getPlanPlace() == null ? null : placeReview.getPlanPlace().getId(),
        placeReview.getTravelRecordPlace() == null ? null : placeReview.getTravelRecordPlace().getId(),
        placeReview.getRating(),
        placeReview.getStayMinutes(),
        placeReview.getContent(),
        List.copyOf(placeReview.getTags()),
        List.copyOf(mediaUrls),
        placeReview.getCreatedAt(),
        placeReview.getUpdatedAt());
  }
}
