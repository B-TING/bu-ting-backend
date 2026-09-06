package com.butingbe.domain.zoneevent.entity;

import com.butingbe.global.common.TimestampEntity;
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
 * 유저 1명이 이벤트 1건에 참여한 로그. 상태 머신을 가진다({@link ParticipationStatus}).
 *
 * <p>이 이슈(#177)는 스키마와 필드만 정의한다. 상태 전이(참여 시작·제출·취소·판정)와 보상 연동은 각각 후속 하위 이슈에서 메서드로 채운다.
 */
@Entity
@Table(name = "zone_event_participation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneEventParticipation extends TimestampEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "participation_id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "event_id", nullable = false)
  private ZoneEvent event;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ParticipationStatus status;

  @Column private Boolean success;

  @Column(name = "media_file_key", length = 512)
  private String mediaFileKey;

  @Column(length = 300)
  private String content;

  @Column(name = "gps_lat", nullable = false)
  private Double gpsLat;

  @Column(name = "gps_lng", nullable = false)
  private Double gpsLng;

  @Column(name = "submit_gps_lat")
  private Double submitGpsLat;

  @Column(name = "submit_gps_lng")
  private Double submitGpsLng;

  @Column(name = "captured_at")
  private OffsetDateTime capturedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private ParticipationVisibility visibility;

  @Column(nullable = false)
  private Boolean hidden;

  @Column(name = "like_count", nullable = false)
  private Long likeCount;

  @Column(name = "comment_count", nullable = false)
  private Integer commentCount;

  @Column(name = "cancel_reason", length = 30)
  private String cancelReason;

  @Column(name = "fail_reason", length = 30)
  private String failReason;

  @Column(name = "joined_at", nullable = false)
  private OffsetDateTime joinedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Column(name = "reviewed_by")
  private UUID reviewedBy;

  @Column(name = "reviewed_at")
  private OffsetDateTime reviewedAt;

  @Builder
  private ZoneEventParticipation(
      ZoneEvent event,
      UUID userId,
      ParticipationStatus status,
      Double gpsLat,
      Double gpsLng,
      OffsetDateTime joinedAt,
      ParticipationVisibility visibility) {
    this.event = event;
    this.userId = userId;
    this.status = status;
    this.gpsLat = gpsLat;
    this.gpsLng = gpsLng;
    this.joinedAt = joinedAt;
    this.visibility = visibility == null ? ParticipationVisibility.PUBLIC : visibility;
    this.hidden = false;
    this.likeCount = 0L;
    this.commentCount = 0;
  }

  /** 반경 검증을 통과한 참여를 JOINED 상태로 시작한다. */
  public static ZoneEventParticipation join(
      ZoneEvent event, UUID userId, double gpsLat, double gpsLng) {
    return ZoneEventParticipation.builder()
        .event(event)
        .userId(userId)
        .status(ParticipationStatus.JOINED)
        .gpsLat(gpsLat)
        .gpsLng(gpsLng)
        .joinedAt(OffsetDateTime.now())
        .build();
  }
}
