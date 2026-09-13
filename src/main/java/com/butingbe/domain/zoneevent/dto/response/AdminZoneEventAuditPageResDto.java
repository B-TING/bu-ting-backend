package com.butingbe.domain.zoneevent.dto.response;

import java.util.List;

public record AdminZoneEventAuditPageResDto(
    List<AdminZoneEventAuditItemResDto> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext) {}
