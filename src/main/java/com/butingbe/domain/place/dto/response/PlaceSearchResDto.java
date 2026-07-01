package com.butingbe.domain.place.dto.response;

import java.util.List;

public record PlaceSearchResDto(int page, int size, int totalCount, List<PlaceResDto> places) {}
