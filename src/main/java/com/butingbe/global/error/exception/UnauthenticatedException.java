package com.butingbe.global.error.exception;

public class UnauthenticatedException extends RuntimeException {

  private static final String DEFAULT_MESSAGE = "인증 정보가 유효하지 않거나 존재하지 않는 회원입니다.";

  public UnauthenticatedException() {
    super(DEFAULT_MESSAGE);
  }

  public UnauthenticatedException(String message) {
    super(message);
  }
}
