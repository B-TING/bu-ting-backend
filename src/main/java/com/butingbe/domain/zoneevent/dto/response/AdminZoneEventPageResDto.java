package com.butingbe.domain.zoneevent.dto.response;

import java.util.List;

/** 운영 이벤트 목록 커서 페이징 응답. */
public record AdminZoneEventPageResDto(
    List<AdminZoneEventResDto> items, String nextCursor, boolean hasNext) {}
