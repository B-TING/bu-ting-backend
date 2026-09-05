package com.butingbe.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.butingbe.domain.place.exception.PlaceKeywordNotFoundException;
import com.butingbe.domain.travel.ai.PlaceKey;
import com.butingbe.domain.travel.ai.TravelPlanValidationException;
import com.butingbe.global.common.ApiResponse;
import com.butingbe.global.error.exception.ConflictException;
import com.butingbe.global.error.exception.DuplicateResourceException;
import com.butingbe.global.error.exception.ForbiddenException;
import com.butingbe.global.error.exception.ResourceNotFoundException;
import com.butingbe.global.error.exception.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;
  private HttpServletRequest request;

  @BeforeEach
  void setUp() {
    StaticMessageSource messageSource = new StaticMessageSource();
    messageSource.addMessage("error.server.internal", Locale.KOREAN, "서버 오류가 발생했습니다.");
    messageSource.addMessage("error.resource.not_found", Locale.KOREAN, "리소스를 찾을 수 없습니다.");
    messageSource.addMessage("error.place.keyword_not_found", Locale.KOREAN, "장소를 찾을 수 없습니다.");
    messageSource.addMessage("error.travel.ai.low_quality_plan", Locale.KOREAN, "생성된 일정 품질이 낮습니다.");

    handler = new GlobalExceptionHandler(messageSource, new FixedLocaleResolver(Locale.KOREAN));
    request = new MockHttpServletRequest();
  }

  @Test
  @DisplayName("AI 응답에서 발생한 일정 검증 실패는 502를 반환한다")
  void travelPlanValidationFromGeneratedResponseReturnsBadGateway() {
    ResponseEntity<ApiResponse<Void>> response =
        handler.handleTravelPlanValidation(
            new TravelPlanValidationException(
                TravelPlanValidationException.Reason.LOW_QUALITY_PLAN, true, Set.<PlaceKey>of()),
            request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(response.getBody().isSuccess()).isFalse();
    assertThat(response.getBody().getMessage()).isEqualTo("생성된 일정 품질이 낮습니다.");
  }

  @Test
  @DisplayName("요청에서 발생한 일정 검증 실패는 400을 반환한다")
  void travelPlanValidationFromRequestReturnsBadRequest() {
    ResponseEntity<ApiResponse<Void>> response =
        handler.handleTravelPlanValidation(
            new TravelPlanValidationException(
                TravelPlanValidationException.Reason.LOW_QUALITY_PLAN, false, Set.<PlaceKey>of()),
            request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("인증 실패는 401을 반환한다")
  void unauthenticatedReturnsUnauthorized() {
    ResponseEntity<ApiResponse<Void>> response =
        handler.handleUnauthenticatedException(new UnauthenticatedException(), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().isSuccess()).isFalse();
  }

  @Test
  @DisplayName("중복 리소스는 409를 반환한다")
  void duplicateResourceReturnsConflict() {
    ResponseEntity<ApiResponse<Void>> response =
        handler.handleDuplicateResourceException(
            new DuplicateResourceException("이미 존재합니다."), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  @DisplayName("충돌 예외는 409와 원본 메시지를 반환한다")
  void conflictReturnsConflict() {
    ResponseEntity<ApiResponse<Void>> response =
        handler.handleConflictException(new ConflictException("상태가 충돌합니다."));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().getMessage()).isEqualTo("상태가 충돌합니다.");
  }

  @Test
  @DisplayName("잘못된 인자는 400과 원본 메시지를 반환한다")
  void illegalArgumentReturnsBadRequest() {
    ResponseEntity<ApiResponse<Void>> response =
        handler.handleIllegalArgumentException(new IllegalArgumentException("잘못된 값입니다."));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().getMessage()).isEqualTo("잘못된 값입니다.");
  }

  @Test
  @DisplayName("권한 없음은 403을 반환한다")
  void forbiddenReturnsForbidden() {
    ResponseEntity<ApiResponse<Void>> response =
        handler.handleForbiddenException(new ForbiddenException("권한이 없습니다."));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().getMessage()).isEqualTo("권한이 없습니다.");
  }

  @Test
  @DisplayName("리소스 없음은 404를 반환한다")
  void resourceNotFoundReturnsNotFound() {
    ResponseEntity<ApiResponse<Void>> response =
        handler.handleResourceNotFoundException(new ResourceNotFoundException("찾을 수 없습니다."));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().getMessage()).isEqualTo("찾을 수 없습니다.");
  }

  @Test
  @DisplayName("장소 키워드 없음은 404와 번역된 메시지를 반환한다")
  void placeKeywordNotFoundReturnsLocalisedNotFound() {
    ResponseEntity<ApiResponse<Void>> response =
        handler.handlePlaceKeywordNotFoundException(new PlaceKeywordNotFoundException(), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().getMessage()).isEqualTo("장소를 찾을 수 없습니다.");
  }

  @Test
  @DisplayName("DTO 검증 실패는 첫 번째 오류 메시지를 400으로 반환한다")
  void validationReturnsFirstErrorMessage() {
    BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
    bindingResult.addError(new ObjectError("request", "제목은 필수입니다."));
    bindingResult.addError(new ObjectError("request", "두 번째 오류"));

    ResponseEntity<ApiResponse<Void>> response =
        handler.handleValidationException(new MethodArgumentNotValidException(null, bindingResult));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().getMessage()).isEqualTo("제목은 필수입니다.");
  }

  @Test
  @DisplayName("정적 리소스 없음은 404와 공통 메시지를 반환한다")
  void noResourceFoundReturnsNotFound() {
    ResponseEntity<ApiResponse<Void>> response =
        handler.handleNoResourceFoundException(
            new NoResourceFoundException(HttpMethod.GET, "/missing", "/missing"), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().getMessage()).isEqualTo("리소스를 찾을 수 없습니다.");
  }

  @Test
  @DisplayName("예상하지 못한 예외는 500과 공통 메시지를 반환한다")
  void unexpectedExceptionReturnsInternalServerError() {
    ResponseEntity<ApiResponse<Void>> response =
        handler.handleException(new RuntimeException("boom"), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().getMessage()).isEqualTo("서버 오류가 발생했습니다.");
  }

  @Test
  @DisplayName("메시지 키가 없으면 키 자체를 메시지로 반환한다")
  void missingMessageKeyFallsBackToCode() {
    ResponseEntity<ApiResponse<Void>> response =
        handler.handleUnauthenticatedException(
            new UnauthenticatedException("error.unknown.key"), request);

    assertThat(response.getBody().getMessage()).isEqualTo("error.unknown.key");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
