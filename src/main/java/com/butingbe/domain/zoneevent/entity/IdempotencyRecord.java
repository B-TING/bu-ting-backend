package com.butingbe.domain.zoneevent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Idempotency-Key 재전송 응답 저장소. 성공 처리 결과만 저장한다(실패한 시도는 저장하지 않음). */
@Entity
@Table(name = "idempotency_key")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord {

  @Id
  @Column(name = "idempotency_key", nullable = false, updatable = false, length = 200)
  private String idempotencyKey;

  @Column(nullable = false, updatable = false, length = 100)
  private String endpoint;

  @Column(nullable = false, updatable = false, length = 300)
  private String fingerprint;

  @Column(name = "response_body", columnDefinition = "text")
  private String responseBody;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  public IdempotencyRecord(
      String idempotencyKey, String endpoint, String fingerprint, String responseBody) {
    this.idempotencyKey = idempotencyKey;
    this.endpoint = endpoint;
    this.fingerprint = fingerprint;
    this.responseBody = responseBody;
    this.createdAt = OffsetDateTime.now();
  }
}
