package com.butingbe.domain.zoneevent.entity;

import com.butingbe.global.common.BaseEntity;
import com.butingbe.global.error.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 특정 구역·타입에 귀속된 미션 1건.
 *
 * <p>구역은 {@code ChatZone} enum 이름 문자열로 저장하고 DB CHECK 제약으로 6종만 허용한다. 보상은 생성 시점 스냅샷(jsonb)으로 고정된다.
 */
@Entity
@Table(name = "zone_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneEvent extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "event_id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "zone_id", nullable = false, length = 30)
  private String zoneId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "type_code", referencedColumnName = "type_code", nullable = false)
  private ZoneEventType type;

  @Column(name = "round_id")
  private UUID roundId;

  @Column(nullable = false)
  private String title;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "starts_at", nullable = false)
  private OffsetDateTime startsAt;

  @Column(name = "duration_minutes", nullable = false)
  private Integer durationMinutes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ZoneEventStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "base_reward", nullable = false, columnDefinition = "jsonb")
  private RewardSnapshot baseReward;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "excellence_reward", columnDefinition = "jsonb")
  private RewardSnapshot excellenceReward;

  @Column(name = "success_limit_per_user", nullable = false)
  private Integer successLimitPerUser;

  @Builder
  private ZoneEvent(
      String zoneId,
      ZoneEventType type,
      UUID roundId,
      String title,
      String description,
      OffsetDateTime startsAt,
      Integer durationMinutes,
      ZoneEventStatus status,
      RewardSnapshot baseReward,
      RewardSnapshot excellenceReward,
      Integer successLimitPerUser) {
    this.zoneId = zoneId;
    this.type = type;
    this.roundId = roundId;
    this.title = title;
    this.description = description;
    this.startsAt = startsAt;
    this.durationMinutes = durationMinutes;
    this.status = status;
    this.baseReward = baseReward;
    this.excellenceReward = excellenceReward;
    this.successLimitPerUser = successLimitPerUser == null ? 1 : successLimitPerUser;
  }

  /** 이벤트 종료 시각. starts_at + duration. */
  public OffsetDateTime endsAt() {
    return startsAt.plusMinutes(durationMinutes);
  }

  /** SCHEDULED → ACTIVE. 다른 상태에서 호출하면 409. */
  public void activate() {
    requireStatus(ZoneEventStatus.SCHEDULED);
    this.status = ZoneEventStatus.ACTIVE;
  }

  /** ACTIVE → CLOSED. */
  public void close() {
    requireStatus(ZoneEventStatus.ACTIVE);
    this.status = ZoneEventStatus.CLOSED;
  }

  /** SCHEDULED/ACTIVE → CANCELLED. 이미 종료·취소된 이벤트는 취소할 수 없다. */
  public void markCancelled() {
    if (status != ZoneEventStatus.SCHEDULED && status != ZoneEventStatus.ACTIVE) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    this.status = ZoneEventStatus.CANCELLED;
  }

  /** 상태와 무관하게 수정 가능한 항목(제목·설명·기간·성공 상한·우수 보상). null은 건너뛴다. */
  public void applyEditable(
      String title,
      String description,
      Integer durationMinutes,
      Integer successLimitPerUser,
      RewardSnapshot excellenceReward,
      boolean excellenceRewardPresent) {
    if (title != null) {
      this.title = title;
    }
    if (description != null) {
      this.description = description;
    }
    if (durationMinutes != null) {
      this.durationMinutes = durationMinutes;
    }
    if (successLimitPerUser != null) {
      this.successLimitPerUser = successLimitPerUser;
    }
    if (excellenceRewardPresent) {
      this.excellenceReward = excellenceReward;
    }
  }

  /** SCHEDULED 상태에서만 바꿀 수 있는 항목(구역·타입·시작 시각·기본 보상). null은 건너뛴다. */
  public void applyScheduledOnly(
      String zoneId, ZoneEventType type, OffsetDateTime startsAt, RewardSnapshot baseReward) {
    if (zoneId != null) {
      this.zoneId = zoneId;
    }
    if (type != null) {
      this.type = type;
    }
    if (startsAt != null) {
      this.startsAt = startsAt;
    }
    if (baseReward != null) {
      this.baseReward = baseReward;
    }
  }

  private void requireStatus(ZoneEventStatus expected) {
    if (status != expected) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
  }
}
