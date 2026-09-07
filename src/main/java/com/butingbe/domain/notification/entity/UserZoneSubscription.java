package com.butingbe.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 유저의 관심 구역 구독. 유저·구역당 하나(UK). */
@Entity
@Table(
    name = "user_zone_subscription",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_user_zone_subscription",
          columnNames = {"user_id", "zone_id"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserZoneSubscription {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "subscription_id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "zone_id", nullable = false, length = 30)
  private String zoneId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Builder
  private UserZoneSubscription(UUID userId, String zoneId) {
    this.userId = userId;
    this.zoneId = zoneId;
    this.createdAt = OffsetDateTime.now();
  }
}
