package com.butingbe.domain.zoneevent.dto.request;

import com.butingbe.domain.zoneevent.entity.RewardSnapshot;

/** 보상 스냅샷 입력(포인트·배지 / 우수 보상 TOP N·상품 코드). */
public record RewardSnapshotReqDto(
    Integer points, String badgeCode, Integer topN, String prizeRewardCode) {

  public RewardSnapshot toSnapshot() {
    return new RewardSnapshot(points, badgeCode, topN, prizeRewardCode);
  }
}
