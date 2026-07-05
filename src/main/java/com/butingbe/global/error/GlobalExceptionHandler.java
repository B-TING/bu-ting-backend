package com.butingbe.global.error;

import com.butingbe.global.common.ApiResponse;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.DuplicateResourceException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice // 💡 JSON 응답을 반환하는 전역 예외 처리기 선언
public class GlobalExceptionHandler {

  private final MessageSource messageSource;
  private final LocaleResolver localeResolver;

  /** 🔑 1. 인증 실패 예외 (401 Unauthorized) 우리가 만든 UnauthenticatedException이 앱 어디서든 터지면 이 메소드가 가로챕니다. */
  @ExceptionHandler(UnauthenticatedException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnauthenticatedException(
      UnauthenticatedException e, HttpServletRequest request) {
    log.warn("Unauthorized Exception 발생: {}", e.getMessage());

    return ResponseEntity.status(HttpStatus.UNAUTHORIZED) // HTTP 상태코드 401 세팅
        .body(ApiResponse.fail(message(e.getMessage(), request)));
  }

  @ExceptionHandler(DuplicateResourceException.class)
  public ResponseEntity<ApiResponse<Void>> handleDuplicateResourceException(
      DuplicateResourceException e, HttpServletRequest request) {
    log.warn("Duplicate Resource Exception 발생: {}", e.getMessage());

    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.fail(message(e.getMessage(), request)));
  }

  /** ❌ 2. 잘못된 비즈니스 요청 예외 (400 Bad Request) IllegalArgumentException 등이 터졌을 때 처리합니다. */
  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ApiResponse<Void>> handleConflictException(ConflictException e) {
    log.warn("Conflict Exception: {}", e.getMessage());

    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail(e.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
      IllegalArgumentException e) {
    log.warn("Bad Request Exception 발생: {}", e.getMessage());

    return ResponseEntity.status(HttpStatus.BAD_REQUEST) // HTTP 상태코드 400 세팅
        .body(ApiResponse.fail(e.getMessage()));
  }

  /**
   * 📝 3. DTO 유효성 검증 실패 예외 (400 Bad Request) 컨트롤러에서 @Valid 선언한 DTO 제약조건(예: @NotBlank)을 위반했을 때
   * 작동합니다.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationException(
      MethodArgumentNotValidException e) {
    // 여러 에러 중 가장 첫 번째 에러 메시지만 쏙 뽑아서 프론트에 깔끔하게 전달
    String errorMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
    log.warn("Validation Exception 발생: {}", errorMessage);

    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(errorMessage));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(
      NoResourceFoundException e, HttpServletRequest request) {
    log.warn("Static Resource Not Found: {}", e.getMessage());

    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.fail(message("error.resource.not_found", request)));
  }

  /**
   * 💥 4. 최후의 보루: 예상치 못한 서버 내부 에러 (500 Internal Server Error) 시스템 에러나 데이터베이스 에러 등 백엔드가 미처 예측하지 못한 찐
   * 에러 처리
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(
      Exception e, HttpServletRequest request) {
    log.error("Unexpected Server Error 발생: ", e); // 500 에러는 추적을 위해 stack trace 로깅 필수

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR) // HTTP 상태코드 500 세팅
        .body(ApiResponse.fail(message("error.server.internal", request)));
  }

  private String message(String code, HttpServletRequest request) {
    Locale locale = localeResolver.resolveLocale(request);
    return messageSource.getMessage(code, null, code, locale);
  }
}
