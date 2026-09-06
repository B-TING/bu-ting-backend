package com.butingbe.domain.auth.security;

import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import org.springframework.stereotype.Component;

/** 운영 API 권한 검사. ROLE_ADMIN 또는 ROLE_MANAGER만 통과한다. */
@Component
public class OperatorAuthorization {

  /** 운영자가 아니면 예외. 미인증은 401, 권한 부족은 403. */
  public void requireOperator(AuthenticatedUser user) {
    if (user == null || user.id() == null) {
      throw new UnauthenticatedException();
    }
    if (!isOperator(user)) {
      throw new ForbiddenException("error.operator.forbidden");
    }
  }

  /** 운영자 여부만 확인한다(예외를 던지지 않음). */
  public boolean isOperator(AuthenticatedUser user) {
    return user != null
        && user.authorities().stream()
            .anyMatch(
                authority ->
                    "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ROLE_MANAGER".equals(authority.getAuthority()));
  }
}
