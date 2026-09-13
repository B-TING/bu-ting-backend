package com.butingbe.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 푸시 발송 이력. */
@Entity
@Table(name = "push_notification_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushNotificationLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(nullable = false, length = 30)
  private String kind;

  @Column(nullable = false, length = 50)
  private String target;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payload;

  @Column(name = "recipient_count", nullable = false)
  private Integer recipientCount;

  @Column(name = "sent_at", nullable = false)
  private OffsetDateTime sentAt;

  @Column(nullable = false, length = 20)
  private String result;

  @Builder
  private PushNotificationLog(
      String kind, String target, Map<String, Object> payload, int recipientCount, String result) {
    this.kind = kind;
    this.target = target;
    this.payload = payload;
    this.recipientCount = recipientCount;
    this.sentAt = OffsetDateTime.now();
    this.result = result;
  }
}
