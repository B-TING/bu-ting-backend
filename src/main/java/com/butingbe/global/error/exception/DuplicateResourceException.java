package com.butingbe.global.error.exception;

public class DuplicateResourceException extends RuntimeException {

  public static final String MESSAGE_CODE = "error.user.email.duplicate";

  public DuplicateResourceException() {
    super(MESSAGE_CODE);
  }

  public DuplicateResourceException(String message) {
    super(message);
  }
}
