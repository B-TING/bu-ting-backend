package com.butingbe.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.domain.auth.security.AuthenticatedUser;
import com.butingbe.domain.auth.security.OperatorAuthorization;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AdminMeServiceTest {

  private final AdminMeService service = new AdminMeService(new OperatorAuthorization());

  @Test
  @DisplayName("ADMIN 사용자의 자기 정보를 반환한다")
  void returnsAdminInfo() {
    UUID userId = UUID.randomUUID();
    AuthenticatedUser admin =
        new AuthenticatedUser(
            userId, "admin@example.com", "운영자", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    var result = service.getMe(admin);

    assertThat(result.userId()).isEqualTo(userId.toString());
    assertThat(result.nickname()).isEqualTo("운영자");
    assertThat(result.role()).isEqualTo("ADMIN");
    assertThat(result.permissions()).containsExactly("ROLE_ADMIN");
  }

  @Test
  @DisplayName("MANAGER 사용자의 자기 정보를 반환한다")
  void returnsManagerInfo() {
    UUID userId = UUID.randomUUID();
    AuthenticatedUser manager =
        new AuthenticatedUser(
            userId,
            "manager@example.com",
            "부관리자",
            List.of(new SimpleGrantedAuthority("ROLE_MANAGER")));

    var result = service.getMe(manager);

    assertThat(result.role()).isEqualTo("MANAGER");
    assertThat(result.permissions()).containsExactly("ROLE_MANAGER");
  }

  @Test
  @DisplayName("미인증이면 401")
  void unauthenticated() {
    assertThatThrownBy(() -> service.getMe(null)).isInstanceOf(UnauthenticatedException.class);
  }

  @Test
  @DisplayName("일반 유저는 403")
  void nonOperatorForbidden() {
    AuthenticatedUser user =
        new AuthenticatedUser(
            UUID.randomUUID(),
            "u@example.com",
            "유저",
            List.of(new SimpleGrantedAuthority("ROLE_USER")));

    assertThatThrownBy(() -> service.getMe(user)).isInstanceOf(ForbiddenException.class);
  }
}
