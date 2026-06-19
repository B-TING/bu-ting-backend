package com.butingbe.global.error.exception;

public class UnauthenticatedException extends RuntimeException {

  public static final String MESSAGE_CODE = "error.auth.unauthenticated";

  public UnauthenticatedException() {
    super(MESSAGE_CODE);
  }

  public UnauthenticatedException(String message) {
    super(message);
  }
}
