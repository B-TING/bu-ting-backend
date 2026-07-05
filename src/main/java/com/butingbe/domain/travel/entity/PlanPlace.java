package com.butingbe.domain.travel.entity;

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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "plan_place",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_plan_place_sequence", columnNames = {"plan_id", "sequence"})
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

  @Column(nullable = false)
  private Integer sequence;

  @Column(name = "place_name", nullable = false)
  private String placeName;

  @Column(nullable = false)
  private String address;

  @Column
  private Double latitude;

  @Column
  private Double longitude;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PlaceProvider provider;

  @Column(name = "provider_place_id", nullable = false)
  private String providerPlaceId;

  @Column(name = "duration_minutes")
  private Integer durationMinutes;

  @Column(name = "is_visited", nullable = false)
  private Boolean visited = false;

  @Builder
  public PlanPlace(
      Plan plan,
      Integer sequence,
      String placeName,
      String address,
      Double latitude,
      Double longitude,
      PlaceProvider provider,
      String providerPlaceId,
      Integer durationMinutes,
      Boolean visited) {
    this.plan = plan;
    this.sequence = sequence;
    this.placeName = placeName;
    this.address = address;
    this.latitude = latitude;
    this.longitude = longitude;
    this.provider = provider;
    this.providerPlaceId = providerPlaceId;
    this.durationMinutes = durationMinutes;
    this.visited = visited != null ? visited : false;
  }

  public void changeSequence(Integer sequence) {
    this.sequence = sequence;
  }
}
