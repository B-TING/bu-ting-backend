package com.butingbe.domain.zonetitle.dto.response;

import com.butingbe.domain.user.entity.User;
import com.butingbe.domain.zonetitle.entity.UserZoneTitle;
import java.time.OffsetDateTime;

public record AdminZoneTitleHolderItemResDto(
    String userTitleId,
    String userId,
    String nickname,
    String email,
    OffsetDateTime earnedAt,
    boolean equipped,
    OffsetDateTime revokedAt) {

  public static AdminZoneTitleHolderItemResDto of(UserZoneTitle title, User user) {
    return new AdminZoneTitleHolderItemResDto(
        title.getId().toString(),
        title.getUserId().toString(),
        user == null ? null : user.getNickname(),
        user == null ? null : user.getEmail(),
        title.getEarnedAt(),
        Boolean.TRUE.equals(title.getEquipped()),
        title.getRevokedAt());
  }
}
