package com.butingbe.domain.travelrecord.entity;

import com.butingbe.domain.travel.entity.PlaceProvider;
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
import java.time.LocalTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "travel_record_place",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_travel_record_place_sequence",
          columnNames = {"travel_record_day_id", "sequence"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelRecordPlace {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "travel_record_day_id", nullable = false)
  private TravelRecordDay travelRecordDay;

  @Column(name = "original_plan_place_id")
  private UUID originalPlanPlaceId;

  @Column(nullable = false)
  private Integer sequence;

  @Column(name = "place_name", nullable = false)
  private String placeName;

  @Column(nullable = false)
  private String address;

  @Column private Double latitude;

  @Column private Double longitude;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PlaceProvider provider;

  @Column(name = "provider_place_id", nullable = false)
  private String providerPlaceId;

  @Column(name = "duration_minutes")
  private Integer durationMinutes;

  @Column(columnDefinition = "text")
  private String memo;

  @Column(name = "scheduled_time")
  private LocalTime scheduledTime;

  @Column(name = "is_visited", nullable = false)
  private Boolean visited = false;

  @Builder
  public TravelRecordPlace(
      TravelRecordDay travelRecordDay,
      UUID originalPlanPlaceId,
      Integer sequence,
      String placeName,
      String address,
      Double latitude,
      Double longitude,
      PlaceProvider provider,
      String providerPlaceId,
      Integer durationMinutes,
      String memo,
      LocalTime scheduledTime,
      Boolean visited) {
    this.travelRecordDay = travelRecordDay;
    this.originalPlanPlaceId = originalPlanPlaceId;
    this.sequence = sequence;
    this.placeName = placeName;
    this.address = address;
    this.latitude = latitude;
    this.longitude = longitude;
    this.provider = provider;
    this.providerPlaceId = providerPlaceId;
    this.durationMinutes = durationMinutes;
    this.memo = memo;
    this.scheduledTime = scheduledTime;
    this.visited = visited != null ? visited : false;
  }
}
