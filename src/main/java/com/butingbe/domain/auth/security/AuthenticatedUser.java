package com.butingbe.domain.auth.security;

import com.butingbe.domain.user.entity.User;
import java.util.Collection;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record AuthenticatedUser(
    UUID id, String email, String nickname, Collection<? extends GrantedAuthority> authorities) {

  public static AuthenticatedUser from(User user) {
    return new AuthenticatedUser(
        user.getId(),
        user.getEmail(),
        user.getNickname(),
        java.util.List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
  }
}
