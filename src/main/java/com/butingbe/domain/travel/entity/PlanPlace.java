package com.butingbe.domain.travel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "plan_place",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_plan_place_google_place_id", columnNames = "google_place_id")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanPlace {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_id", nullable = false)
  private Plan plan;

  @Column(name = "google_place_id", nullable = false)
  private String googlePlaceId;

  @Column(name = "place_name")
  private String placeName;

  @Column
  private Integer sequence;

  @Column(name = "duration_time")
  private LocalTime durationTime;

  @Column(name = "transport_type")
  private String transportType;

  @Column(name = "transport_duration")
  private LocalTime transportDuration;

  @Column(name = "is_visited")
  private Boolean visited = false;

  @Column
  private Double latitude;

  @Column
  private Double longitude;

  @Column
  private String addr;

  @Builder
  public PlanPlace(
      Plan plan,
      String googlePlaceId,
      String placeName,
      Integer sequence,
      LocalTime durationTime,
      String transportType,
      LocalTime transportDuration,
      Boolean visited,
      Double latitude,
      Double longitude,
      String addr) {
    this.plan = plan;
    this.googlePlaceId = googlePlaceId;
    this.placeName = placeName;
    this.sequence = sequence;
    this.durationTime = durationTime;
    this.transportType = transportType;
    this.transportDuration = transportDuration;
    this.visited = visited != null ? visited : false;
    this.latitude = latitude;
    this.longitude = longitude;
    this.addr = addr;
  }
}
