package com.butingbe.domain.zoneevent.dto.response;

import java.util.List;

/** 검수 큐 커서 페이징 응답. */
public record ReviewQueuePageResDto(
    List<ReviewQueueItemResDto> items, String nextCursor, boolean hasNext) {}
