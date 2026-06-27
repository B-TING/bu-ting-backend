package com.butingbe.domain.user.dto.response;

import com.butingbe.domain.user.entity.Name;
import com.butingbe.domain.user.entity.User;

public record MyProfileResDto(
    String userId,
    String email,
    String nickname,
    String profileImageUrl,
    String provider,
    String firstName,
    String lastName) {

  public static MyProfileResDto from(User user) {
    Name name = user.getName();
    return new MyProfileResDto(
        user.getId() != null ? user.getId().toString() : null,
        user.getEmail(),
        user.getNickname(),
        user.getProfileImageUrl(),
        user.getProvider(),
        name != null ? name.getFirstName() : null,
        name != null ? name.getLastName() : null);
  }
}
