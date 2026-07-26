package com.butingbe.domain.travelrecord.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record TravelRecordUpdateReqDto(
    @Size(max = 100, message = "Travel record title must be 100 characters or less.") String title,
    String content,
    @Size(max = 1000, message = "Cover image URL must be 1000 characters or less.")
        String coverImageUrl,
    @Min(value = 1, message = "Travel record overall rating must be at least 1.")
        @Max(value = 5, message = "Travel record overall rating must be at most 5.")
        Integer overallRating) {

  public TravelRecordUpdateReqDto(String title, String content, String coverImageUrl) {
    this(title, content, coverImageUrl, null);
  }
}
