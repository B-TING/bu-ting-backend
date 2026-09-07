package com.butingbe.domain.auth.dto.response;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;

public record AdminMeResDto(String userId, String nickname, String role, List<String> permissions) {

  private static final String ROLE_PREFIX = "ROLE_";

  public static AdminMeResDto from(AuthenticatedUser user) {
    List<String> authorities =
        user.authorities().stream().map(GrantedAuthority::getAuthority).toList();
    String role =
        authorities.stream()
            .filter(authority -> authority.startsWith(ROLE_PREFIX))
            .map(authority -> authority.substring(ROLE_PREFIX.length()))
            .findFirst()
            .orElse(null);
    return new AdminMeResDto(user.id().toString(), user.nickname(), role, authorities);
  }
}
