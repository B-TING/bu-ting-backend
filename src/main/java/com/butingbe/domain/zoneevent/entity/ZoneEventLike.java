package com.butingbe.domain.zoneevent.entity;

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

/** 공개 참여에 대한 좋아요. 유저·참여당 하나(UK). 참여는 id로만 참조한다. */
@Entity
@Table(
    name = "zone_event_like",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_zone_event_like_participation_user",
          columnNames = {"participation_id", "user_id"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneEventLike {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "like_id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "participation_id", nullable = false)
  private UUID participationId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Builder
  private ZoneEventLike(UUID participationId, UUID userId) {
    this.participationId = participationId;
    this.userId = userId;
    this.createdAt = OffsetDateTime.now();
  }
}
