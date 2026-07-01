package com.butingbe.domain.place.dto.googleplaces;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GooglePlaceSearchRequest(
    String textQuery,
    String languageCode,
    String regionCode,
    Integer maxResultCount,
    LocationBias locationBias) {

  public record LocationBias(Circle circle) {}

  public record Circle(Coordinate center, Double radius) {}

  public record Coordinate(Double latitude, Double longitude) {}
}
