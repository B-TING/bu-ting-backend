package com.butingbe.domain.travelrecord.dto.response;

import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travelrecord.entity.PlaceReview;
import com.butingbe.domain.travelrecord.entity.TravelRecord;
import com.butingbe.domain.travelrecord.entity.TravelRecordPlace;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PlaceReviewSummaryResDto(
    PlaceProvider provider,
    String providerPlaceId,
    long reviewCount,
    double averageRating,
    Map<Integer, Long> ratingCounts,
    List<PlaceReviewItemResDto> reviews) {

  public static PlaceReviewSummaryResDto of(
      PlaceProvider provider,
      String providerPlaceId,
      double averageRating,
      Map<Integer, Long> ratingCounts,
      List<PlaceReview> reviews) {
    return new PlaceReviewSummaryResDto(
        provider,
        providerPlaceId,
        reviews.size(),
        averageRating,
        ratingCounts,
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
          placeReview.getCreatedAt(),
          placeReview.getUpdatedAt());
    }
  }
}
