package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.zoneevent.entity.ZoneEventLike;
import java.time.OffsetDateTime;

/** 좋아요 결과. */
public record LikeResDto(
    String likeId, String participationId, OffsetDateTime likedAt, long likeCount) {

  public static LikeResDto of(ZoneEventLike like, long likeCount) {
    return new LikeResDto(
        like.getId().toString(),
        like.getParticipationId().toString(),
        like.getCreatedAt(),
        likeCount);
  }
}
