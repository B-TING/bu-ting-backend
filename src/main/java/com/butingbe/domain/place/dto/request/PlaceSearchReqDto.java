package com.butingbe.domain.place.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PlaceSearchReqDto(
    @Min(1) Integer page,
    @Min(1) @Max(100) Integer size,
    String districtCode,
    String contentTypeId) {

  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_SIZE = 20;

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

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
