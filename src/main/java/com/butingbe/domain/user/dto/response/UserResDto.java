package com.butingbe.domain.user.dto.response;

import com.butingbe.domain.user.entity.User;

public record UserResDto(String email, String nickname) {
  public static UserResDto from(User user) {
    return new UserResDto(user.getEmail(), user.getNickname());
  }
}
