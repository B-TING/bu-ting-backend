package com.butingbe.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 알림 유형별 수신 여부 override. 행이 없으면 기본 켜짐으로 본다. */
@Entity
@Table(
    name = "user_notification_preference",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_user_notification_preference",
          columnNames = {"user_id", "notification_type"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNotificationPreference {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "preference_id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "notification_type", nullable = false, length = 30)
  private NotificationType notificationType;

  @Column(nullable = false)
  private Boolean enabled;

  @Builder
  private UserNotificationPreference(
      UUID userId, NotificationType notificationType, boolean enabled) {
    this.userId = userId;
    this.notificationType = notificationType;
    this.enabled = enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
