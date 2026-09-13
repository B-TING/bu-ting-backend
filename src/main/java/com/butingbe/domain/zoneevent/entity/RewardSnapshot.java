package com.butingbe.domain.zoneevent.entity;

/**
 * 이벤트 생성 시점에 고정되는 보상 스냅샷(jsonb).
 *
 * <p>카탈로그가 나중에 바뀌어도 이 값은 소급되지 않는다(BR-06). base_reward는 포인트·배지, excellence_reward는 우수 보상(TOP N + 상품
 * 코드)에 쓴다. 두 용도가 필드를 공유하므로 쓰지 않는 필드는 null로 둔다.
 */
public record RewardSnapshot(
    Integer points, String badgeCode, Integer topN, String prizeRewardCode) {}
