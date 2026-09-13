package com.butingbe.domain.zonetitle.dto.response;

import java.util.List;

public record AdminZoneTitleHolderPageResDto(
    List<AdminZoneTitleHolderItemResDto> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext) {}
