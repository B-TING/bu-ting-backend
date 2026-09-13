package com.butingbe.domain.reward.entity;

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

/**
 * 보상 지급 기록.
 *
 * <p>참여·이벤트는 도메인 경계를 넘지 않도록 엔티티가 아니라 id(UUID)로 느슨하게 참조한다(FK 무결성은 DB가 보장). 회수는 {@code revokedAt}으로
 * 무효 처리한다.
 */
@Entity
@Table(name = "reward_grant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RewardGrant {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "grant_id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reward_id", nullable = false)
  private RewardCatalog reward;

  @Column(name = "participation_id")
  private UUID participationId;

  @Column(name = "event_id")
  private UUID eventId;

  @Column(name = "round_id")
  private UUID roundId;

  @Enumerated(EnumType.STRING)
  @Column(name = "grant_reason", nullable = false, length = 30)
  private GrantReason grantReason;

  @Column(name = "granted_at", nullable = false)
  private OffsetDateTime grantedAt;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  @Builder
  private RewardGrant(
      UUID userId,
      RewardCatalog reward,
      UUID participationId,
      UUID eventId,
      UUID roundId,
      GrantReason grantReason,
      OffsetDateTime grantedAt) {
    this.userId = userId;
    this.reward = reward;
    this.participationId = participationId;
    this.eventId = eventId;
    this.roundId = roundId;
    this.grantReason = grantReason;
    this.grantedAt = grantedAt;
  }

  /** 어뷰징 회수. 이미 회수됐으면 그대로 둔다. */
  public void revoke(OffsetDateTime at) {
    if (revokedAt == null) {
      revokedAt = at;
    }
  }
}
