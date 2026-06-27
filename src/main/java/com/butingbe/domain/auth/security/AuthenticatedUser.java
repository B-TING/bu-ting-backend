package com.butingbe.domain.auth.security;

import com.butingbe.domain.user.entity.User;
import java.util.Collection;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record AuthenticatedUser(
    UUID id, String email, String nickname, Collection<? extends GrantedAuthority> authorities) {

  private static final String DEVELOPMENT_ADMIN_EMAIL = "admin@local.dev";

  public static AuthenticatedUser from(User user) {
    return new AuthenticatedUser(
        user.getId(),
        user.getEmail(),
        user.getNickname(),
        java.util.List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
  }

  public static AuthenticatedUser developmentAdmin() {
    return new AuthenticatedUser(
        null,
        DEVELOPMENT_ADMIN_EMAIL,
        "개발 관리자",
        java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  public boolean isDevelopmentAdmin() {
    return DEVELOPMENT_ADMIN_EMAIL.equals(email)
        && authorities.stream()
            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
  }
}
