package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventParticipation;
import com.butingbe.domain.zoneevent.entity.ZoneEventSubmission;
import com.butingbe.domain.zonetitle.dto.response.EquippedTitleResDto;
import java.util.List;

/** 검수 승인/반려 처리 결과. 승인은 SUCCESS·앨범 공개만 하며 보상은 지급하지 않는다(별도 확정 단계, 이슈 #244). */
public record AdminReviewDecisionResDto(
    String participationId,
    String status,
    String submissionId,
    int attemptNo,
    String reviewStatus,
    List<EquippedTitleResDto> newlyAwardedTitles) {

  public static AdminReviewDecisionResDto of(
      ZoneEventParticipation p, ZoneEventSubmission s, List<EquippedTitleResDto> titles) {
    return new AdminReviewDecisionResDto(
        p.getId().toString(), p.getStatus().name(), s.getId().toString(), s.getAttemptNo(),
        s.getReviewStatus().name(), titles);
  }
}
