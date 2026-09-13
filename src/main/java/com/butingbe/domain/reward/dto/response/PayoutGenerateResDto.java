package com.butingbe.domain.reward.dto.response;

/** TOP_LIKE 지급 후보 생성 결과 요약. */
public record PayoutGenerateResDto(
    String eventId, int created, int alreadyExists, int heldOnCreate) {}
