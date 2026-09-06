package com.butingbe.domain.zoneevent.entity;

import com.butingbe.global.common.BaseEntity;
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
}
