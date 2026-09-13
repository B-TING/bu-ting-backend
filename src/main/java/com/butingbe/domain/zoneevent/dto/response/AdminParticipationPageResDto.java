package com.butingbe.domain.zoneevent.dto.response;

import java.util.List;

/** page/size 기반 페이지 응답(1-based page). */
public record AdminParticipationPageResDto(
    List<AdminParticipationListItemResDto> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext) {}
