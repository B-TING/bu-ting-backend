package com.butingbe.domain.zoneevent.service;

import com.butingbe.domain.zoneevent.entity.IdempotencyRecord;
import com.butingbe.domain.zoneevent.repository.IdempotencyRecordRepository;
import com.butingbe.global.error.exception.ConflictException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Idempotency-Key 헤더 처리. 같은 키로 재전송되면 처음 처리했던 결과를 그대로 돌려준다. 키가 없으면(null/blank) 아무 것도 하지 않는다
 * — 이 헤더는 선택적이다.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

  private final IdempotencyRecordRepository repository;
  private final ObjectMapper objectMapper;

  /**
   * 재생할 이전 응답이 있으면 그 JSON(바디가 없었으면 빈 문자열)을 돌려준다. 같은 키인데 endpoint·fingerprint가 다르면 다른 요청에
   * 키가 잘못 재사용된 것이므로 409.
   */
  @Transactional(readOnly = true)
  public Optional<String> findReplay(String idempotencyKey, String endpoint, String fingerprint) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return Optional.empty();
    }
    return repository
        .findById(idempotencyKey)
        .map(
            record -> {
              if (!record.getEndpoint().equals(endpoint)
                  || !record.getFingerprint().equals(fingerprint)) {
                throw new ConflictException("error.zone_event.review.idempotency_key_conflict");
              }
              return record.getResponseBody() == null ? "" : record.getResponseBody();
            });
  }

  /** 처리 성공 결과를 저장한다. 키가 없으면 아무 것도 하지 않는다. */
  @Transactional
  public void save(String idempotencyKey, String endpoint, String fingerprint, Object responseBody) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return;
    }
    String json = responseBody == null ? "" : writeJson(responseBody);
    repository.save(new IdempotencyRecord(idempotencyKey, endpoint, fingerprint, json));
  }

  private String writeJson(Object responseBody) {
    try {
      return objectMapper.writeValueAsString(responseBody);
    } catch (JacksonException e) {
      throw new IllegalStateException("Failed to serialize idempotent response.", e);
    }
  }
}
