package com.butingbe.global.common;

import lombok.Getter;

@Getter
public class ApiResponse<T> {
  private final boolean success;
  private final String message;
  private final T data;

  private ApiResponse(boolean success, String message, T data) {
    this.success = success;
    this.message = message;
    this.data = data;
  }

  // 🟢 성공 시 호출하는 메서드
  public static <T> ApiResponse<T> success(String message, T data) {
    return new ApiResponse<>(true, message, data);
  }

  // 🔴 실패 시 호출하는 메서드
  public static <T> ApiResponse<Void> fail(String message) {
    return new ApiResponse<>(false, message, null);
  }
}
