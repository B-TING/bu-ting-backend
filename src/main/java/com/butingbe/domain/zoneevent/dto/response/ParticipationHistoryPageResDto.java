package com.butingbe.domain.zoneevent.dto.response;

import java.util.List;

/** 커서 페이징 응답. */
public record ParticipationHistoryPageResDto(
    List<ParticipationHistoryItemResDto> items, String nextCursor, boolean hasNext) {}
