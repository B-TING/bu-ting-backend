package com.butingbe.global.error.exception;

/**
 * 클라이언트 요청이 잘못됐을 때 쓴다. 400 으로 나간다.
 *
 * <p>예전에는 이 자리에 {@link IllegalArgumentException} 을 썼다. 그러면 JDK 와 라이브러리가 던지는 것까지 같은 핸들러에 걸려, {@code
 * UUID.fromString} 이나 {@code Enum.valueOf} 의 내부 메시지가 그대로 400 으로 나갔다. 서버 버그가 클라이언트 오류로 위장된다.
 *
 * <p>전용 타입을 쓰면 "우리가 의도한 400" 과 "예상하지 못한 오류" 가 갈린다. 후자는 500 으로 떨어져 추적 가능해진다.
 */
public class InvalidRequestException extends RuntimeException {

  public InvalidRequestException(String message) {
    super(message);
  }
}
