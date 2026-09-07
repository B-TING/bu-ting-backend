package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.RewardSnapshot;

/**
 * 이벤트 보상 스냅샷 요약.
 *
 * <p>이벤트 생성 시점에 고정된 값(포인트·배지 코드 / 우수 보상 TOP N·상품 코드)을 그대로 노출한다. 배지·상품의 표시 이름은 reward 도메인 카탈로그가
 * 필요하므로 여기서는 코드만 담는다(조회 도메인을 reward에 결합하지 않는다).
 */
public record RewardSummaryResDto(
    Integer points, String badgeCode, Integer topN, String prizeRewardCode) {

  public static RewardSummaryResDto from(RewardSnapshot snapshot) {
    if (snapshot == null) {
      return null;
    }
    return new RewardSummaryResDto(
        snapshot.points(), snapshot.badgeCode(), snapshot.topN(), snapshot.prizeRewardCode());
  }
}
