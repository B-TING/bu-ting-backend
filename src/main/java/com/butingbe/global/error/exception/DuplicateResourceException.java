package com.butingbe.global.error.exception;

public class DuplicateResourceException extends RuntimeException {

  private static final String DEFAULT_MESSAGE = "이미 가입된 이메일 주소입니다.";

  public DuplicateResourceException() {
    super(DEFAULT_MESSAGE);
  }

  public DuplicateResourceException(String message) {
    super(message);
  }
}
