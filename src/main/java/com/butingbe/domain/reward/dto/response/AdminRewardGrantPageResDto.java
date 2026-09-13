package com.butingbe.domain.reward.dto.response;

import java.util.List;

/** 지급 이력 커서 페이징 응답. */
public record AdminRewardGrantPageResDto(
    List<AdminRewardGrantResDto> items, String nextCursor, boolean hasNext) {}
