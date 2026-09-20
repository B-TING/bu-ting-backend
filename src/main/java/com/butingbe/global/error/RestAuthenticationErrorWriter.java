package com.butingbe.global.error;

import com.butingbe.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.ObjectMapper;

/**
 * 필터 체인에서 걸린 인증·인가 실패를 {@link GlobalExceptionHandler} 와 같은 {@link ApiResponse} 형식으로 내려준다.
 *
 * <p>필터 단계의 실패는 컨트롤러에 닿지 못해 {@code @RestControllerAdvice} 가 잡지 못한다. 여기서 같은 모양으로 쓰지 않으면 클라이언트가 401 만
 * 두 가지 형식으로 상대하게 된다.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationErrorWriter
    implements AuthenticationEntryPoint, AccessDeniedHandler {

  private static final String UNAUTHENTICATED_CODE = "error.auth.unauthenticated";
  private static final String FORBIDDEN_CODE = "error.operator.forbidden";

  private final ObjectMapper objectMapper;
  private final MessageSource messageSource;
  private final LocaleResolver localeResolver;

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    write(request, response, HttpStatus.UNAUTHORIZED, UNAUTHENTICATED_CODE);
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
      throws IOException {
    write(request, response, HttpStatus.FORBIDDEN, FORBIDDEN_CODE);
  }

  private void write(
      HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code)
      throws IOException {
    Locale locale = localeResolver.resolveLocale(request);
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(
        response.getOutputStream(),
        ApiResponse.fail(messageSource.getMessage(code, null, code, locale)));
  }
}
