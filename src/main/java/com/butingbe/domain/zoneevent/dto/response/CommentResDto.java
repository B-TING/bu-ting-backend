package com.butingbe.domain.zoneevent.dto.response;

import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.zoneevent.entity.ZoneEventComment;
import com.butingbe.domain.zonetitle.dto.response.EquippedTitleResDto;
import java.time.OffsetDateTime;

/** 댓글 응답. equippedTitle은 칭호 이슈에서 채워진다. */
public record CommentResDto(
    String commentId,
    String participationId,
    String authorId,
    String authorNickname,
    String authorProfileImageUrl,
    Object equippedTitle,
    String content,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static CommentResDto of(ZoneEventComment comment, User author) {
    return of(comment, author, null);
  }

  public static CommentResDto of(
      ZoneEventComment comment, User author, EquippedTitleResDto equippedTitle) {
    return new CommentResDto(
        comment.getId().toString(),
        comment.getParticipationId().toString(),
        comment.getUserId().toString(),
        author == null ? null : author.getNickname(),
        author == null ? null : author.getProfileImageUrl(),
        equippedTitle,
        comment.getContent(),
        comment.getCreatedAt(),
        comment.getUpdatedAt());
  }
}
