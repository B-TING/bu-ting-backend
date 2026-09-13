package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventRankingSnapshot;

/** Top N 경계 후보 한 명. */
public record TopNCandidateResDto(
    String snapshotId,
    String participationId,
    Integer rankN,
    Long likeCountAtClose,
    boolean tied,
    boolean heldByReport,
    boolean finalized) {

  public static TopNCandidateResDto of(ZoneEventRankingSnapshot snapshot, boolean heldByReport) {
    return new TopNCandidateResDto(
        snapshot.getId().toString(),
        snapshot.getParticipationId().toString(),
        snapshot.getRankN(),
        snapshot.getLikeCountAtClose(),
        snapshot.getTied(),
        heldByReport,
        snapshot.getFinalized());
  }
}
