package com.butingbe.domain.reward.dto.response;

import java.util.List;

public record AdminRewardPayoutPageResDto(
    List<AdminRewardPayoutListItemResDto> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext) {}
