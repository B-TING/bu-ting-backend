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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회차별 우천 대체 타겟. 운영자가 ACTIVE 이벤트 타겟을 이것으로 즉시 교체할 수 있다(FR-RND-05). */
@Entity
@Table(name = "zone_event_backup_target")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneEventBackupTarget extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "target_id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "round_id", nullable = false)
  private ZoneEventRound round;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_kind", nullable = false, length = 20)
  private ZoneEventTargetKind targetKind;

  @Column(name = "landmark_id", length = 100)
  private String landmarkId;

  @Column(name = "place_name", nullable = false)
  private String placeName;

  @Column(name = "guide_text", columnDefinition = "text")
  private String guideText;

  @Column(name = "example_file_key", length = 512)
  private String exampleFileKey;

  @Column(nullable = false)
  private Double latitude;

  @Column(nullable = false)
  private Double longitude;

  @Column(name = "radius_m", nullable = false)
  private Integer radiusM;

  @Builder
  private ZoneEventBackupTarget(
      ZoneEventRound round,
      ZoneEventTargetKind targetKind,
      String landmarkId,
      String placeName,
      String guideText,
      String exampleFileKey,
      Double latitude,
      Double longitude,
      Integer radiusM) {
    this.round = round;
    this.targetKind = targetKind;
    this.landmarkId = landmarkId;
    this.placeName = placeName;
    this.guideText = guideText;
    this.exampleFileKey = exampleFileKey;
    this.latitude = latitude;
    this.longitude = longitude;
    this.radiusM = radiusM;
  }
}
