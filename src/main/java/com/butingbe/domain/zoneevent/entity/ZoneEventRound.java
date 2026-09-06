package com.butingbe.domain.zoneevent.entity;

import com.butingbe.global.common.BaseEntity;
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

/** 이벤트가 동시에 열리는 운영 단위. v1은 1일(KST 10:00 → 익일 10:00). */
@Entity
@Table(name = "zone_event_round")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneEventRound extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "round_id", nullable = false, updatable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "round_type", nullable = false, length = 20)
  private RoundType roundType;

  @Column(name = "starts_at", nullable = false)
  private OffsetDateTime startsAt;

  @Column(name = "ends_at", nullable = false)
  private OffsetDateTime endsAt;

  @Column(nullable = false, length = 40)
  private String timezone;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RoundStatus status;

  @Column(name = "settled_at")
  private OffsetDateTime settledAt;

  @Builder
  private ZoneEventRound(
      RoundType roundType,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      String timezone,
      RoundStatus status) {
    this.roundType = roundType == null ? RoundType.REGULAR : roundType;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.timezone = timezone == null ? "Asia/Seoul" : timezone;
    this.status = status == null ? RoundStatus.SCHEDULED : status;
  }

  /** SCHEDULED → OPEN. */
  public void open() {
    this.status = RoundStatus.OPEN;
  }

  /** OPEN → CLOSED. */
  public void close() {
    this.status = RoundStatus.CLOSED;
  }

  /** 정산 완료 표식. 멱등: 이미 SETTLED면 그대로 둔다. */
  public void settle(OffsetDateTime at) {
    if (status != RoundStatus.SETTLED) {
      this.status = RoundStatus.SETTLED;
      this.settledAt = at;
    }
  }
}
