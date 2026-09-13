package com.butingbe.domain.reward.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/** 유저 보상 요약. 포인트 잔액, 구역별로 묶은 배지, 쿠폰(Phase 2부터 채워짐). */
public record UserRewardsResDto(int pointBalance, List<BadgeGroup> badges, List<Object> coupons) {

  /** 한 구역의 배지 묶음("배지 월"). */
  public record BadgeGroup(String zoneId, List<BadgeItem> items) {}

  public record BadgeItem(String code, String name, String imageUrl, OffsetDateTime earnedAt) {}
}
