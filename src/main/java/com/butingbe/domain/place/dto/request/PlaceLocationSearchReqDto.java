package com.butingbe.domain.place.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PlaceLocationSearchReqDto(
    @Min(1) Integer page,
    @Min(1) @Max(100) Integer size,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double mapX,
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double mapY,
    @NotNull @Min(1) @Max(20000) Integer radius,
    String contentTypeId,
    String arrange) {

  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_SIZE = 20;
  private static final String DEFAULT_ARRANGE = "E";

  public int pageOrDefault() {
    return page == null ? DEFAULT_PAGE : page;
  }

  public int sizeOrDefault() {
    return size == null ? DEFAULT_SIZE : size;
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
