package com.butingbe.domain.reward.dto.response;

import java.util.List;

/** 기본 보상 지급 결과: 지급된 보상 목록 + 지급 후 포인트 잔액. */
public record BaseRewardResult(List<GrantedRewardDto> rewards, int pointBalance) {}
