package com.butingbe.domain.place.entity;

import com.butingbe.domain.chat.entity.ChatZone;
import com.butingbe.global.common.TimestampEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서버가 보관하는 장소 카탈로그의 한 건.
 *
 * <p>{@code provider}와 {@code providerPlaceId}가 식별 기준이다. AI 일정 생성의 {@code PlaceKey}와 같은 체계라 후보 생성
 * 결과를 그대로 기존 검증 경로에 넘길 수 있다.
 *
 * <p>{@code dwellMinutes}, {@code rating}, {@code reviewCount}, {@code preferredTimeSlot}은 외부 API가
 * 주지 않거나 보강이 필요한 값이라 적재 직후에는 비어 있다.
 */
@Entity
@Table(name = "place")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place extends TimestampEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(nullable = false, length = 20)
  private String provider;

  @Column(name = "provider_place_id", nullable = false, length = 64)
  private String providerPlaceId;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(length = 300)
  private String address;

  private Double latitude;

  private Double longitude;

  @Column(name = "content_type_id", length = 10)
  private String contentTypeId;

  @Column(name = "image_url", length = 500)
  private String imageUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "zone_id", length = 30)
  private ChatZone zoneId;

  @Column(name = "district_code", length = 10)
  private String districtCode;

  @Column(name = "dwell_minutes")
  private Integer dwellMinutes;

  @Column(precision = 2, scale = 1)
  private BigDecimal rating;

  @Column(name = "review_count")
  private Integer reviewCount;

  @Enumerated(EnumType.STRING)
  @Column(name = "preferred_time_slot", length = 20)
  private PlaceTimeSlot preferredTimeSlot;

  @Builder
  private Place(
      String provider,
      String providerPlaceId,
      String name,
      String address,
      Double latitude,
      Double longitude,
      String contentTypeId,
      String imageUrl,
      ChatZone zoneId,
      String districtCode,
      Integer dwellMinutes,
      BigDecimal rating,
      Integer reviewCount,
      PlaceTimeSlot preferredTimeSlot) {
    this.provider = provider;
    this.providerPlaceId = providerPlaceId;
    this.name = name;
    this.address = address;
    this.latitude = latitude;
    this.longitude = longitude;
    this.contentTypeId = contentTypeId;
    this.imageUrl = imageUrl;
    this.zoneId = zoneId;
    this.districtCode = districtCode;
    this.dwellMinutes = dwellMinutes;
    this.rating = rating;
    this.reviewCount = reviewCount;
    this.preferredTimeSlot = preferredTimeSlot;
  }

  /** 동기화로 다시 받은 원본 값을 반영한다. 보강 컬럼(체류 시간·평점·시간대)은 건드리지 않는다. */
  public void applySync(
      String name,
      String address,
      Double latitude,
      Double longitude,
      String contentTypeId,
      String imageUrl,
      ChatZone zoneId,
      String districtCode) {
    this.name = name;
    this.address = address;
    this.latitude = latitude;
    this.longitude = longitude;
    this.contentTypeId = contentTypeId;
    this.imageUrl = imageUrl;
    this.zoneId = zoneId;
    this.districtCode = districtCode;
  }
}
