package com.butingbe.domain.user.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserRole {

  // 스프링 시큐리티의 권한 명명 규칙인 "ROLE_" 접두사를 결합합니다.
  USER("ROLE_USER", "일반 회원"),
  ADMIN("ROLE_ADMIN", "최고 관리자"),
  MANAGER("ROLE_MANAGER", "부관리자");

  private final String key;
  private final String title;
}
