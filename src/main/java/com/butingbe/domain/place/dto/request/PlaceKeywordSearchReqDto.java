package com.butingbe.domain.place.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlaceKeywordSearchReqDto(
    @NotBlank @Size(max = 100) String keyword,
    @Min(1) Integer page,
    @Min(1) @Max(100) Integer size,
    String districtCode,
    String contentTypeId,
    String arrange) {

  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_SIZE = 20;
  private static final String DEFAULT_ARRANGE = "A";

  public String normalizedKeyword() {
    return keyword.trim();
  }

  public int pageOrDefault() {
    return page == null ? DEFAULT_PAGE : page;
  }

  public int sizeOrDefault() {
    return size == null ? DEFAULT_SIZE : size;
  }

  public String districtCodeOrNull() {
    return hasText(districtCode) ? districtCode : null;
  }

  public String contentTypeIdOrNull() {
    return hasText(contentTypeId) ? contentTypeId : null;
  }

  public String arrangeOrDefault() {
    return hasText(arrange) ? arrange.trim().toUpperCase() : DEFAULT_ARRANGE;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
