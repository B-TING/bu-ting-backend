package com.butingbe.domain.zoneevent.entity;

import com.butingbe.global.common.BaseEntity;
import com.butingbe.global.error.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

  /** 관리자 페이지에 노출할 회차 번호. 관리자가 생성 시점에 직접 지정한다(중복 시 409). */
  @Column(name = "round_no", nullable = false)
  private Integer roundNo;

  @Column(length = 255)
  private String name;

  @Column(name = "starts_at", nullable = false)
  private OffsetDateTime startsAt;

  @Column(name = "ends_at", nullable = false)
  private OffsetDateTime endsAt;

  @Column(nullable = false, length = 40)
  private String timezone;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RoundStatus status;

  @Column(name = "closed_at")
  private OffsetDateTime closedAt;

  @Column(name = "settled_at")
  private OffsetDateTime settledAt;

  @Column(name = "cancel_reason", length = 300)
  private String cancelReason;

  /** 회차 공통 기본 우수 보상(TOP N 포함). 구역 슬롯 생성 시 개별로 안 넘기면 이 값을 물려받는다. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "excellence_reward", columnDefinition = "jsonb")
  private RewardSnapshot excellenceReward;

  @Version
  @Column(nullable = false)
  private Long revision;

  @Builder
  private ZoneEventRound(
      RoundType roundType,
      Integer roundNo,
      String name,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      String timezone,
      RoundStatus status,
      RewardSnapshot excellenceReward) {
    this.roundType = roundType == null ? RoundType.REGULAR : roundType;
    this.roundNo = roundNo;
    this.name = name;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.timezone = timezone == null ? "Asia/Seoul" : timezone;
    this.status = status == null ? RoundStatus.DRAFT : status;
    this.excellenceReward = excellenceReward;
  }

  /** DRAFT → SCHEDULED. DRAFT가 아니면 409. */
  public void confirmSchedule() {
    requireStatus(RoundStatus.DRAFT);
    this.status = RoundStatus.SCHEDULED;
  }

  /** SCHEDULED → ACTIVE. SCHEDULED가 아니면 409. */
  public void activate() {
    requireStatus(RoundStatus.SCHEDULED);
    this.status = RoundStatus.ACTIVE;
  }

  /** ACTIVE → CLOSED. ACTIVE가 아니면 409. */
  public void close() {
    requireStatus(RoundStatus.ACTIVE);
    this.status = RoundStatus.CLOSED;
    this.closedAt = OffsetDateTime.now();
  }

  /** DRAFT/SCHEDULED/ACTIVE → CANCELLED. 그 외 상태면 409. */
  public void cancel(String reason) {
    if (status != RoundStatus.DRAFT && status != RoundStatus.SCHEDULED && status != RoundStatus.ACTIVE) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    this.status = RoundStatus.CANCELLED;
    this.cancelReason = reason;
  }

  /** DRAFT/SCHEDULED에서만 메타데이터 수정 가능. null은 미변경. 그 외 상태면 409. */
  public void applyEditable(
      String name, OffsetDateTime startsAt, OffsetDateTime endsAt, String timezone, RoundType roundType) {
    if (status != RoundStatus.DRAFT && status != RoundStatus.SCHEDULED) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    if (name != null) {
      this.name = name;
    }
    if (startsAt != null) {
      this.startsAt = startsAt;
    }
    if (endsAt != null) {
      this.endsAt = endsAt;
    }
    if (timezone != null) {
      this.timezone = timezone;
    }
    if (roundType != null) {
      this.roundType = roundType;
    }
  }

  /** 정산 완료 표식. 멱등: 이미 SETTLED면 그대로 둔다. */
  public void settle(OffsetDateTime at) {
    if (status != RoundStatus.SETTLED) {
      this.status = RoundStatus.SETTLED;
      this.settledAt = at;
    }
  }

  private void requireStatus(RoundStatus expected) {
    if (status != expected) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
  }
}
