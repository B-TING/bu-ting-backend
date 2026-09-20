package com.butingbe.domain.travelrecord.dto.response;

import com.butingbe.domain.travelrecord.entity.TravelRecordLike;
import java.time.LocalDateTime;
import java.util.UUID;

public record TravelRecordLikeResDto(
    UUID likeId, UUID travelRecordId, LocalDateTime likedAt, long likeCount) {

  /** likeCount 는 원자적 update 뒤에 다시 읽은 값이다. 엔티티는 갱신 전 값을 들고 있어 쓸 수 없다. */
  public static TravelRecordLikeResDto from(TravelRecordLike like, long likeCount) {
    return new TravelRecordLikeResDto(
        like.getId(), like.getTravelRecord().getId(), like.getCreatedAt(), likeCount);
  }
}
