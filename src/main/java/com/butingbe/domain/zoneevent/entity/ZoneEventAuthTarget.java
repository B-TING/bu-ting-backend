package com.butingbe.domain.zoneevent.entity;

import com.butingbe.global.common.BaseEntity;
import com.butingbe.global.error.exception.ConflictException;
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
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 인증 이벤트가 요구하는 선택 장소/사물 + GPS 중심 좌표 + 반경. 이벤트당 여러 개를 동시에 가질 수 있다(v2). */
@Entity
@Table(name = "zone_event_auth_target")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneEventAuthTarget extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "target_id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "event_id", nullable = false)
  private ZoneEvent event;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_kind", nullable = false, length = 20)
  private ZoneEventTargetKind targetKind;

  @Column(name = "landmark_id", length = 100)
  private String landmarkId;

  /** 기존 관광지 API의 contentId. PLACE 종류 타겟에만 쓰인다. */
  @Column(name = "place_content_id", length = 100)
  private String placeContentId;

  @Column(name = "content_type_id", length = 20)
  private String contentTypeId;

  @Column(name = "place_name", nullable = false)
  private String placeName;

  @Column(name = "guide_text", columnDefinition = "text")
  private String guideText;

  @Column(name = "example_file_key", length = 512)
  private String exampleFileKey;

  /** 관광지 원본 좌표 스냅샷. 관리자가 수정해도 바뀌지 않는다. */
  @Column(name = "source_latitude")
  private Double sourceLatitude;

  @Column(name = "source_longitude")
  private Double sourceLongitude;

  /** 실제 인증에 쓰는 중심 좌표. 원본과 다르면 관리자가 수정한 것이다. */
  @Column(nullable = false)
  private Double latitude;

  @Column(nullable = false)
  private Double longitude;

  @Column(name = "radius_m", nullable = false)
  private Integer radiusM;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ZoneEventTargetStatus status;

  @Version
  @Column(nullable = false)
  private Long revision;

  @Builder
  private ZoneEventAuthTarget(
      ZoneEvent event,
      ZoneEventTargetKind targetKind,
      String landmarkId,
      String placeContentId,
      String contentTypeId,
      String placeName,
      String guideText,
      String exampleFileKey,
      Double sourceLatitude,
      Double sourceLongitude,
      Double latitude,
      Double longitude,
      Integer radiusM) {
    this.event = event;
    this.targetKind = targetKind;
    this.landmarkId = landmarkId;
    this.placeContentId = placeContentId;
    this.contentTypeId = contentTypeId;
    this.placeName = placeName;
    this.guideText = guideText;
    this.exampleFileKey = exampleFileKey;
    this.sourceLatitude = sourceLatitude == null ? latitude : sourceLatitude;
    this.sourceLongitude = sourceLongitude == null ? longitude : sourceLongitude;
    this.latitude = latitude;
    this.longitude = longitude;
    this.radiusM = radiusM;
    this.status = ZoneEventTargetStatus.ACTIVE;
  }

  /** 관리자가 이 타겟을 취소한다(ACTIVE만 가능). 기존 제출 이력은 보존된다. */
  public void cancel() {
    if (status != ZoneEventTargetStatus.ACTIVE) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    this.status = ZoneEventTargetStatus.CANCELLED;
  }

  /** 다른 contentId로 긴급 교체되어 REPLACED 상태가 된다(ACTIVE만 가능). 새 ACTIVE 타겟은 서비스 계층에서 별도로 만든다. */
  public void markReplaced() {
    if (status != ZoneEventTargetStatus.ACTIVE) {
      throw new ConflictException("error.zone_event.invalid_state");
    }
    this.status = ZoneEventTargetStatus.REPLACED;
  }

  /** 원본 좌표에서 관리자가 인증 중심 좌표를 수정했는지. */
  public boolean isCoordinatesOverridden() {
    return !latitude.equals(sourceLatitude) || !longitude.equals(sourceLongitude);
  }

  /** 타겟 좌표·반경·가이드·예시 이미지를 수정한다(우천 대체 등). null은 건너뛴다. */
  public void update(
      String placeName,
      String guideText,
      String exampleFileKey,
      Double latitude,
      Double longitude,
      Integer radiusM) {
    if (placeName != null) {
      this.placeName = placeName;
    }
    if (guideText != null) {
      this.guideText = guideText;
    }
    if (exampleFileKey != null) {
      this.exampleFileKey = exampleFileKey;
    }
    if (latitude != null) {
      this.latitude = latitude;
    }
    if (longitude != null) {
      this.longitude = longitude;
    }
    if (radiusM != null) {
      this.radiusM = radiusM;
    }
  }
}
