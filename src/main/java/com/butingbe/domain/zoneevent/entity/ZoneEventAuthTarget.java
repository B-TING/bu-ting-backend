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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 인증 이벤트가 요구하는 장소/사물 + GPS 중심 좌표 + 반경. 이벤트당 정확히 1개(v1). */
@Entity
@Table(name = "zone_event_auth_target")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneEventAuthTarget extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "target_id", nullable = false, updatable = false)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "event_id", nullable = false, unique = true)
  private ZoneEvent event;

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
  private ZoneEventAuthTarget(
      ZoneEvent event,
      ZoneEventTargetKind targetKind,
      String landmarkId,
      String placeName,
      String guideText,
      String exampleFileKey,
      Double latitude,
      Double longitude,
      Integer radiusM) {
    this.event = event;
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
