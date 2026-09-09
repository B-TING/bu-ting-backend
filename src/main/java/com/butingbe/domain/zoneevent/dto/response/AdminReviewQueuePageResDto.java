package com.butingbe.domain.zoneevent.dto.response;

import java.util.List;

public record AdminReviewQueuePageResDto(
    List<AdminReviewQueueItemResDto> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext) {}
