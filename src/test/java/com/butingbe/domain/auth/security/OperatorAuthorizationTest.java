package com.butingbe.domain.auth.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class OperatorAuthorizationTest {

  private final OperatorAuthorization operatorAuthorization = new OperatorAuthorization();

  @Test
  @DisplayName("ADMIN/MANAGER는 통과한다")
  void operatorPasses() {
    assertThatCode(() -> operatorAuthorization.requireOperator(operator("ROLE_ADMIN")))
        .doesNotThrowAnyException();
    assertThatCode(() -> operatorAuthorization.requireOperator(operator("ROLE_MANAGER")))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("미인증은 401이다")
  void unauthenticated() {
    assertThatThrownBy(() -> operatorAuthorization.requireOperator(null))
        .isInstanceOf(UnauthenticatedException.class);
  }

  @Test
  @DisplayName("일반 유저는 403이다")
  void nonOperatorForbidden() {
    assertThatThrownBy(() -> operatorAuthorization.requireOperator(operator("ROLE_USER")))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("error.operator.forbidden");
  }

  private AuthenticatedUser operator(String role) {
    return new AuthenticatedUser(
        UUID.randomUUID(), "u@example.com", "u", List.of(new SimpleGrantedAuthority(role)));
  }
}
