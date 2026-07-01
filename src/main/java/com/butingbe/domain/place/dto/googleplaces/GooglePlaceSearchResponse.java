package com.butingbe.domain.place.dto.googleplaces;

import java.util.List;

public record GooglePlaceSearchResponse(List<Place> places) {

  public record Place(String id) {}
}
