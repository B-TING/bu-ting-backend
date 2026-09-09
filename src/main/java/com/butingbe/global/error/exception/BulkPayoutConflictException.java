package com.butingbe.global.error.exception;

import java.util.List;
import lombok.Getter;

/** 일괄 처리 중 일부 대상이 조건을 충족하지 않아 아무 것도 반영하지 않았을 때(all-or-nothing) 던진다. */
@Getter
public class BulkPayoutConflictException extends RuntimeException {

  private final List<String> problemPayoutIds;

  public BulkPayoutConflictException(String message, List<String> problemPayoutIds) {
    super(message);
    this.problemPayoutIds = problemPayoutIds;
  }
}
