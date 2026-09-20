package com.butingbe.domain.auth.entity;

/** 발급 토큰의 용도. */
public enum OpaqueTokenType {
  /** API 호출에 쓰는 짧은 수명의 토큰. */
  ACCESS,
  /** 액세스 토큰을 다시 받기 위한 긴 수명의 토큰. 이것으로 API를 호출할 수는 없다. */
  REFRESH
}
