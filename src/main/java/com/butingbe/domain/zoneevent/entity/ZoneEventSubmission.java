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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 참여 1건의 제출 시도 1회. 반려 후 재제출은 새 row로 쌓이고 이전 이력은 바뀌지 않는다.
 *
 * <p>타겟·좌표·반경·안내 문구는 제출 시점 스냅샷이다. 이후 관리자가 타겟을 수정·교체해도 이미 만들어진 제출의 검수 기준은 바뀌지 않는다.
 */
@Entity
@Table(
    name = "zone_event_submission",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_zone_event_submission_attempt",
          columnNames = {"participation_id", "attempt_no"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneEventSubmission extends TimestampEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "submission_id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "participation_id", nullable = false)
  private ZoneEventParticipation participation;

  @Column(name = "attempt_no", nullable = false)
  private Integer attemptNo;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "target_id", nullable = false)
  private ZoneEventAuthTarget target;

  @Column(name = "place_name", nullable = false)
  private String placeName;

  @Column(name = "target_latitude", nullable = false)
  private Double targetLatitude;

  @Column(name = "target_longitude", nullable = false)
  private Double targetLongitude;

  @Column(name = "radius_m", nullable = false)
  private Integer radiusM;

  @Column(name = "guide_text_snapshot", columnDefinition = "text")
  private String guideTextSnapshot;

  @Column(name = "media_file_key", nullable = false, length = 512)
  private String mediaFileKey;

  @Column(name = "gps_lat", nullable = false)
  private Double gpsLat;

  @Column(name = "gps_lng", nullable = false)
  private Double gpsLng;

  @Column(name = "captured_at")
  private OffsetDateTime capturedAt;

  @Column(name = "submitted_at", nullable = false)
  private OffsetDateTime submittedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "review_status", nullable = false, length = 20)
  private SubmissionReviewStatus reviewStatus;

  @Column(name = "rejection_reason", length = 300)
  private String rejectionReason;

  @Column(name = "reviewed_by")
  private UUID reviewedBy;

  @Column(name = "reviewed_at")
  private OffsetDateTime reviewedAt;

  @Version
  @Column(nullable = false)
  private Long revision;

  @Builder
  private ZoneEventSubmission(
      ZoneEventParticipation participation,
      Integer attemptNo,
      ZoneEventAuthTarget target,
      String placeName,
      Double targetLatitude,
      Double targetLongitude,
      Integer radiusM,
      String guideTextSnapshot,
      String mediaFileKey,
      Double gpsLat,
      Double gpsLng,
      OffsetDateTime capturedAt) {
    this.participation = participation;
    this.attemptNo = attemptNo;
    this.target = target;
    this.placeName = placeName;
    this.targetLatitude = targetLatitude;
    this.targetLongitude = targetLongitude;
    this.radiusM = radiusM;
    this.guideTextSnapshot = guideTextSnapshot;
    this.mediaFileKey = mediaFileKey;
    this.gpsLat = gpsLat;
    this.gpsLng = gpsLng;
    this.capturedAt = capturedAt;
    this.submittedAt = OffsetDateTime.now();
    this.reviewStatus = SubmissionReviewStatus.UNDER_REVIEW;
  }

  /** 검수 승인. */
  public void approve(UUID reviewerId) {
    this.reviewStatus = SubmissionReviewStatus.SUCCESS;
    this.reviewedBy = reviewerId;
    this.reviewedAt = OffsetDateTime.now();
  }

  /** 검수 반려. 같은 참여 건은 새 제출로 재시도할 수 있다. */
  public void reject(UUID reviewerId, String reason) {
    this.reviewStatus = SubmissionReviewStatus.REJECTED;
    this.rejectionReason = reason;
    this.reviewedBy = reviewerId;
    this.reviewedAt = OffsetDateTime.now();
  }
}
