package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.reward.dto.response.GrantedRewardDto;
import java.util.List;

/**
 * 제출 결과.
 *
 * <p>AUTO 판정에서 성공하면 참여는 SUCCESS이고 지급된 보상과 잔액을 함께 돌려준다. 검수 대기(MANUAL/HYBRID)면 참여는 UNDER_REVIEW이고 보상은
 * 비어 있다. {@code newlyEarnedTitles}·{@code titleProgress}는 Phase 2에서 채워진다.
 */
public record SubmitResultResDto(
    ParticipationResDto participation,
    List<GrantedRewardDto> rewards,
    int pointBalance,
    List<Object> newlyEarnedTitles,
    Object titleProgress) {

  public static SubmitResultResDto of(
      ParticipationResDto participation, List<GrantedRewardDto> rewards, int pointBalance) {
    return new SubmitResultResDto(participation, rewards, pointBalance, List.of(), null);
  }
}
