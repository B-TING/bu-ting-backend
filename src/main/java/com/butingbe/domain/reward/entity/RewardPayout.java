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

/**
 * TOP_LIKE 특별 보상 지급 건. 확정된 수상자만 대상으로 생성되며, 사진 승인·기본 보상과는 독립된 지급 건이다.
 *
 * <p>참여·이벤트는 {@link RewardGrant}와 같은 이유로 id(UUID)로 느슨하게 참조한다.
 */
@Entity
@Table(
    name = "reward_payout",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_reward_payout_participation", columnNames = "participation_id")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RewardPayout extends TimestampEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "payout_id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "participation_id", nullable = false)
  private UUID participationId;

  @Column(name = "rank_n", nullable = false)
  private Integer rankN;

  @Column(name = "like_count_at_close", nullable = false)
  private Long likeCountAtClose;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private RewardSnapshot reward;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RewardPayoutStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "hold_status", nullable = false, length = 20)
  private PayoutHoldStatus holdStatus;

  @Column(name = "scheduled_at")
  private OffsetDateTime scheduledAt;

  @Column(name = "confirmed_by")
  private UUID confirmedBy;

  @Column(name = "confirmed_at")
  private OffsetDateTime confirmedAt;

  @Column(name = "mailed_at")
  private OffsetDateTime mailedAt;

  @Column(name = "information_collected_at")
  private OffsetDateTime informationCollectedAt;

  @Column(name = "sent_at")
  private OffsetDateTime sentAt;

  @Column(name = "failure_code", length = 50)
  private String failureCode;

  @Column(columnDefinition = "text")
  private String memo;

  @Column(length = 255)
  private String reference;

  @Version
  @Column(nullable = false)
  private Long revision;

  @Builder
  private RewardPayout(
      UUID eventId,
      UUID participationId,
      Integer rankN,
      Long likeCountAtClose,
      RewardSnapshot reward) {
    this.eventId = eventId;
    this.participationId = participationId;
    this.rankN = rankN;
    this.likeCountAtClose = likeCountAtClose;
    this.reward = reward;
    this.status = RewardPayoutStatus.PENDING_ASSIGN;
    this.holdStatus = PayoutHoldStatus.NONE;
  }

  /** 신고 접수로 지급을 보류한다. 진행 단계(status)는 그대로 둔다. */
  public void hold() {
    this.holdStatus = PayoutHoldStatus.HELD_REPORT;
  }

  /** 이미 발송·지급이 끝났거나 실패 처리된 건은 보류 대상이 아니다(SENT/MAIL_SENT/INFO_COLLECTED/FAILED 제외). */
  public boolean isHoldable() {
    return status != RewardPayoutStatus.SENT
        && status != RewardPayoutStatus.MAIL_SENT
        && status != RewardPayoutStatus.INFO_COLLECTED
        && status != RewardPayoutStatus.FAILED;
  }

  /** 미해결 신고가 없음을 확인한 뒤 보류를 해제한다. */
  public void releaseHold() {
    this.holdStatus = PayoutHoldStatus.NONE;
  }

  /** 참여 회수 등으로 이 지급을 더 이상 진행하지 않는다. 이미 발송된 건은 호출부에서 걸러야 한다. */
  public void fail(String failureCode) {
    this.status = RewardPayoutStatus.FAILED;
    this.failureCode = failureCode;
  }

  /** 보상 항목을 설정·변경한다. PENDING_ASSIGN이었는데 상품 코드까지 채워지면 PENDING_CONFIRM으로 전진한다. */
  public void assignReward(RewardSnapshot reward) {
    this.reward = reward;
    if (this.status == RewardPayoutStatus.PENDING_ASSIGN
        && reward != null
        && reward.prizeRewardCode() != null) {
      this.status = RewardPayoutStatus.PENDING_CONFIRM;
    }
  }

  /** PENDING_CONFIRM에서만 호출 가능(서비스가 상태를 먼저 검사한다). */
  public void confirm(UUID operatorId) {
    this.status = RewardPayoutStatus.CONFIRMED;
    this.confirmedBy = operatorId;
    this.confirmedAt = OffsetDateTime.now();
  }

  public void markMailSent(OffsetDateTime mailedAt, String note) {
    this.status = RewardPayoutStatus.MAIL_SENT;
    this.mailedAt = mailedAt;
    if (note != null) {
      this.memo = note;
    }
  }

  public void markInfoCollected(OffsetDateTime informationCollectedAt, String note) {
    this.status = RewardPayoutStatus.INFO_COLLECTED;
    this.informationCollectedAt = informationCollectedAt;
    if (note != null) {
      this.memo = note;
    }
  }

  public void markSent(OffsetDateTime sentAt, String reference, String note) {
    this.status = RewardPayoutStatus.SENT;
    this.sentAt = sentAt;
    this.reference = reference;
    if (note != null) {
      this.memo = note;
    }
  }

  public void updateMemo(String memo) {
    this.memo = memo;
  }

  public void updateSchedule(OffsetDateTime scheduledAt) {
    this.scheduledAt = scheduledAt;
  }
}
