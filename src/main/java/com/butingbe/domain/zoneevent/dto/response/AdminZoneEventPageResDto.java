package com.butingbe.domain.zoneevent.dto.response;

import java.util.List;

/** 운영 이벤트 목록 페이지 응답. */
public record AdminZoneEventPageResDto(
    List<AdminZoneEventResDto> items, int page, int size, long totalElements, int totalPages) {}
