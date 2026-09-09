package com.butingbe.domain.zoneevent.dto.response;

/** 신고 상세에 함께 보여줄 관련 지급 건 요약. payoutType은 TOP_LIKE(RewardPayout) 또는 BASE(BaseRewardPayout). */
public record AdminZoneEventReportPayoutResDto(
    String payoutId, String payoutType, String status, String holdStatus) {}
