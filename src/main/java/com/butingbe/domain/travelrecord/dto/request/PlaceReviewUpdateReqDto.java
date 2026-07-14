package com.butingbe.domain.travelrecord.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

public record PlaceReviewUpdateReqDto(
    @Min(value = 1, message = "Place review rating must be at least 1.")
        @Max(value = 5, message = "Place review rating must be at most 5.")
        Integer rating,
    String content,
    List<String> tags) {

  public PlaceReviewUpdateReqDto(Integer rating, String content) {
    this(rating, content, null);
  }
}
