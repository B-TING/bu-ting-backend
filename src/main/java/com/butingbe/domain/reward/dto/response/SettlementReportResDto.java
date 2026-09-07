package com.butingbe.domain.reward.dto.response;

import java.util.List;

/** 회차 TOP_LIKE 정산 리포트. 이벤트별 지급/스킵 내역. */
public record SettlementReportResDto(String roundId, List<EventPrizes> events) {

  public record EventPrizes(String eventId, List<Prize> prizes) {}

  public record Prize(String userId, String participationId, String rewardCode, String status) {}
}
