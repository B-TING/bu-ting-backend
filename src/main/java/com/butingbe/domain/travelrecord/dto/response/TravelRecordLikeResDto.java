package com.butingbe.domain.travelrecord.dto.response;

import com.butingbe.domain.travelrecord.entity.TravelRecordLike;
import java.time.LocalDateTime;
import java.util.UUID;

public record TravelRecordLikeResDto(
    UUID likeId, UUID travelRecordId, LocalDateTime likedAt, long likeCount) {

  public static TravelRecordLikeResDto from(TravelRecordLike like) {
    return new TravelRecordLikeResDto(
        like.getId(),
        like.getTravelRecord().getId(),
        like.getCreatedAt(),
        like.getTravelRecord().getLikeCount());
  }
}
