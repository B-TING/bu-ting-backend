package com.butingbe.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 유저의 FCM 디바이스 토큰. 기기별 다수 허용, 토큰은 유일(UK). */
@Entity
@Table(name = "user_device_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDeviceToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "token_id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "fcm_token", nullable = false, length = 512)
  private String fcmToken;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DevicePlatform platform;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Builder
  private UserDeviceToken(UUID userId, String fcmToken, DevicePlatform platform) {
    this.userId = userId;
    this.fcmToken = fcmToken;
    this.platform = platform;
    this.updatedAt = OffsetDateTime.now();
  }

  public void touch(UUID userId, DevicePlatform platform) {
    this.userId = userId;
    this.platform = platform;
    this.updatedAt = OffsetDateTime.now();
  }
}
