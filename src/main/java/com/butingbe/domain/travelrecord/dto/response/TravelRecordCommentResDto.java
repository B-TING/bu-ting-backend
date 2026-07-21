package com.butingbe.domain.travelrecord.dto.response;

import com.butingbe.domain.travelrecord.entity.TravelRecordComment;
import java.time.LocalDateTime;
import java.util.UUID;

public record TravelRecordCommentResDto(
    UUID commentId,
    UUID travelRecordId,
    UUID authorId,
    String authorNickname,
    String authorProfileImageUrl,
    String content,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static TravelRecordCommentResDto from(TravelRecordComment comment) {
    return new TravelRecordCommentResDto(
        comment.getId(),
        comment.getTravelRecord().getId(),
        comment.getAuthor().getId(),
        comment.getAuthor().getNickname(),
        comment.getAuthor().getProfileImageUrl(),
        comment.getContent(),
        comment.getCreatedAt(),
        comment.getUpdatedAt());
  }
}
