package com.butingbe.domain.travelrecord.dto.response;

import com.butingbe.domain.travelrecord.entity.PlaceReview;
import com.butingbe.domain.travelrecord.entity.TravelRecord;
import com.butingbe.domain.travelrecord.entity.TravelRecordPlace;
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
      List<PlaceReview> reviews) {
    return new PlaceReviewSummaryResDto(
        placeId, reviews.size(), averageRating, ratingCounts,
        reviews.stream().map(PlaceReviewItemResDto::from).toList());
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
      String content,
      List<String> tags,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {

    public static PlaceReviewItemResDto from(PlaceReview placeReview) {
      TravelRecordPlace place = placeReview.getTravelRecordPlace();
      TravelRecord travelRecord = place.getTravelRecordDay().getTravelRecord();

      return new PlaceReviewItemResDto(
          placeReview.getId(),
          travelRecord.getId(),
          travelRecord.getTitle(),
          travelRecord.getAuthor().getId(),
          travelRecord.getAuthor().getNickname(),
          place.getId(),
          place.getPlaceName(),
          placeReview.getRating(),
          placeReview.getContent(),
          List.copyOf(placeReview.getTags()),
          placeReview.getCreatedAt(),
          placeReview.getUpdatedAt());
    }
  }
}
