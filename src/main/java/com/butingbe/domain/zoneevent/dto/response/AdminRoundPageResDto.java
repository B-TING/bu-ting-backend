package com.butingbe.domain.zoneevent.dto.response;

import java.util.List;

/** 운영 회차 목록 페이지 응답. */
public record AdminRoundPageResDto(
    List<AdminRoundResDto> items, int page, int size, long totalElements, int totalPages) {}
