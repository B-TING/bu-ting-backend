package com.butingbe.domain.travelrecord.dto.response;

import java.util.List;

public record TravelRecordFeedPageResDto(
    List<TravelRecordFeedResDto> items, String nextCursor, boolean hasNext) {}
