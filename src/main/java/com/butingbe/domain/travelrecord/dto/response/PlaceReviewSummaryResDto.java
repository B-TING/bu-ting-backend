package com.butingbe.domain.travelrecord.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PlaceReviewSummaryResDto(
    String placeId,
    long reviewCount,
    double averageRating,
    Map<Integer, Long> ratingCounts,
    List<PlaceReviewItemResDto> reviews) {

  public static PlaceReviewSummaryResDto of(
      String placeId,
      double averageRating,
      Map<Integer, Long> ratingCounts,
      List<PlaceReviewItemResDto> reviews) {
    return new PlaceReviewSummaryResDto(
        placeId, reviews.size(), averageRating, ratingCounts, List.copyOf(reviews));
  }

  public record PlaceReviewItemResDto(
      UUID placeReviewId,
      UUID travelRecordId,
      String travelRecordTitle,
      UUID authorId,
      String authorNickname,
      UUID travelRecordPlaceId,
      String placeName,
      Integer rating,
      Integer stayMinutes,
      String content,
      List<String> tags,
      List<String> mediaUrls,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {}
}
