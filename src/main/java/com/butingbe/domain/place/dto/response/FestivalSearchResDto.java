package com.butingbe.domain.place.dto.response;

import java.util.List;

public record FestivalSearchResDto(
    String eventStartDate,
    String eventEndDate,
    int page,
    int size,
    int totalCount,
    List<FestivalResDto> festivals) {}
