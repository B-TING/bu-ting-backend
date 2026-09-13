package com.butingbe.domain.reward.dto.response;

import java.util.List;

/** 포인트 원장 커서 페이징 응답. */
public record PointLedgerPageResDto(
    List<PointLedgerItemResDto> items, String nextCursor, boolean hasNext) {}
