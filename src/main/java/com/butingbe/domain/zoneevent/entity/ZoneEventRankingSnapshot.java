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

/**
 * 회차 종료 시각의 좋아요 순위 스냅샷. 한 행이 이벤트 하나의 참여 하나·순위 하나를 나타낸다.
 *
 * <p>{@code tied}는 같은 좋아요 수를 가진 경계 순위 여부를 나타내며, 동점 처리는 관리자가 수상자를 확정할 때까지 자동으로 풀리지 않는다.
 */
@Entity
@Table(
    name = "zone_event_ranking_snapshot",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_zone_event_ranking_snapshot",
          columnNames = {"event_id", "version", "participation_id"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneEventRankingSnapshot {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "snapshot_id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "closed_at", nullable = false)
  private OffsetDateTime closedAt;

  @Column(nullable = false)
  private Integer version;

  @Column(name = "participation_id", nullable = false)
  private UUID participationId;

  @Column(name = "rank_n", nullable = false)
  private Integer rankN;

  @Column(name = "like_count_at_close", nullable = false)
  private Long likeCountAtClose;

  @Column(nullable = false)
  private Boolean tied;

  @Column(nullable = false)
  private Boolean finalized;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Builder
  private ZoneEventRankingSnapshot(
      UUID eventId,
      OffsetDateTime closedAt,
      Integer version,
      UUID participationId,
      Integer rankN,
      Long likeCountAtClose,
      Boolean tied) {
    this.eventId = eventId;
    this.closedAt = closedAt;
    this.version = version == null ? 1 : version;
    this.participationId = participationId;
    this.rankN = rankN;
    this.likeCountAtClose = likeCountAtClose;
    this.tied = Boolean.TRUE.equals(tied);
    this.finalized = false;
    this.createdAt = OffsetDateTime.now();
  }

  /** 관리자가 이 참여를 최종 수상자로 확정했음을 표시한다. */
  public void markFinalized() {
    this.finalized = true;
  }
}
