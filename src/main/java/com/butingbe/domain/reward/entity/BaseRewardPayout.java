package com.butingbe.domain.reward.entity;

import com.butingbe.domain.zoneevent.entity.RewardSnapshot;
import com.butingbe.global.common.TimestampEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 성공 사진 승인과 별도로 확정하는 기본 보상(포인트·배지) 지급 건. */
@Entity
@Table(
    name = "base_reward_payout",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_base_reward_payout_participation",
          columnNames = "participation_id")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BaseRewardPayout extends TimestampEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "payout_id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "participation_id", nullable = false)
  private UUID participationId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private RewardSnapshot reward;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private BaseRewardPayoutStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "hold_status", nullable = false, length = 20)
  private PayoutHoldStatus holdStatus;

  @Column(name = "scheduled_at")
  private OffsetDateTime scheduledAt;

  @Column(name = "confirmed_by")
  private UUID confirmedBy;

  @Column(name = "confirmed_at")
  private OffsetDateTime confirmedAt;

  @Column(name = "paid_at")
  private OffsetDateTime paidAt;

  @Column(name = "failure_code", length = 50)
  private String failureCode;

  @Column(columnDefinition = "text")
  private String memo;

  @Version
  @Column(nullable = false)
  private Long revision;

  @Builder
  private BaseRewardPayout(UUID participationId, RewardSnapshot reward) {
    this.participationId = participationId;
    this.reward = reward;
    this.status = BaseRewardPayoutStatus.PENDING_CONFIRM;
    this.holdStatus = PayoutHoldStatus.NONE;
  }

  /** 신고 접수로 지급을 보류한다. */
  public void hold() {
    this.holdStatus = PayoutHoldStatus.HELD_REPORT;
  }

  /** 이미 지급이 끝났거나 실패 처리된 건은 보류 대상이 아니다(PAID/FAILED 제외). */
  public boolean isHoldable() {
    return status != BaseRewardPayoutStatus.PAID && status != BaseRewardPayoutStatus.FAILED;
  }

  /** 미해결 신고가 없음을 확인한 뒤 보류를 해제한다. */
  public void releaseHold() {
    this.holdStatus = PayoutHoldStatus.NONE;
  }

  public void updateReward(RewardSnapshot reward) {
    this.reward = reward;
  }

  public void updateMemo(String memo) {
    this.memo = memo;
  }

  public void updateSchedule(OffsetDateTime scheduledAt) {
    this.scheduledAt = scheduledAt;
  }

  public void confirm(UUID operatorId) {
    this.status = BaseRewardPayoutStatus.CONFIRMED;
    this.confirmedBy = operatorId;
    this.confirmedAt = OffsetDateTime.now();
  }

  public void markSent(OffsetDateTime paidAt, String note) {
    this.status = BaseRewardPayoutStatus.PAID;
    this.paidAt = paidAt;
    if (note != null) {
      this.memo = note;
    }
  }
}
