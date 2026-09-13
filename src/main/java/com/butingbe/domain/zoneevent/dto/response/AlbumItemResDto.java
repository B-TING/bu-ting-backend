package com.butingbe.domain.zoneevent.dto.response;

import java.time.OffsetDateTime;

/** 앨범 한 항목. equippedTitle은 칭호 이슈(#201)에서 채워진다. */
public record AlbumItemResDto(
    String participationId,
    String eventId,
    String eventTitle,
    String zoneId,
    String authorId,
    String authorNickname,
    String authorProfileImageUrl,
    Object equippedTitle,
    String content,
    String mediaUrl,
    Integer mediaUrlExpiresIn,
    long likeCount,
    boolean likedByMe,
    int commentCount,
    boolean isMine,
    OffsetDateTime completedAt) {}
