package com.butingbe.domain.travelrecord.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PlaceReviewCreateReqDto(
    @NotNull(message = "Place review rating is required.")
        @Min(value = 1, message = "Place review rating must be at least 1.")
        @Max(value = 5, message = "Place review rating must be at most 5.")
        Integer rating,
    String content,
    List<String> tags,
    @Min(value = 0, message = "Stay minutes must be 0 or greater.") Integer stayMinutes,
    List<String> mediaUrls) {

  public PlaceReviewCreateReqDto(Integer rating, String content) {
    this(rating, content, null, null, null);
  }

  public PlaceReviewCreateReqDto(Integer rating, String content, List<String> tags) {
    this(rating, content, tags, null, null);
  }
}
