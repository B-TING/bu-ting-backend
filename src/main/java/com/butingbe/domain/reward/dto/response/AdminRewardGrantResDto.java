package com.butingbe.domain.reward.dto.response;

import com.butingbe.domain.reward.entity.RewardGrant;
import java.time.OffsetDateTime;

/** 운영용 지급 이력 항목. */
public record AdminRewardGrantResDto(
    String grantId,
    String userId,
    String participationId,
    String eventId,
    String grantReason,
    OffsetDateTime grantedAt,
    OffsetDateTime revokedAt) {

  public static AdminRewardGrantResDto from(RewardGrant grant) {
    return new AdminRewardGrantResDto(
        grant.getId().toString(),
        grant.getUserId().toString(),
        grant.getParticipationId() == null ? null : grant.getParticipationId().toString(),
        grant.getEventId() == null ? null : grant.getEventId().toString(),
        grant.getGrantReason().name(),
        grant.getGrantedAt(),
        grant.getRevokedAt());
  }
}
