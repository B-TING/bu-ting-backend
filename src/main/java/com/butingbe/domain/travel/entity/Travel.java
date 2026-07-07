package com.butingbe.domain.travel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "travel")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Travel {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(length = 15)
  private String title;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private TravelStatus status;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "has_heavy_baggage")
  private Boolean hasHeavyBaggage;

  @Column(name = "has_pets")
  private Boolean hasPets;

  @Enumerated(EnumType.STRING)
  @Column(name = "travel_style", length = 30)
  private TravelStyle travelStyle;

  @Column(name = "prefer_flat_terrain")
  private Boolean preferFlatTerrain;

  @Enumerated(EnumType.STRING)
  @Column(length = 30)
  private TravelPace pace;

  @Column(name = "companion_count")
  private Integer companionCount;

  @Column(name = "preferred_foods")
  private String preferredFoods;

  @Enumerated(EnumType.STRING)
  @Column(name = "companion_types", length = 30)
  private CompanionType companionTypes;

  @Column(name = "accommodation_area")
  private String accommodationArea;

  @Builder
  public Travel(
      String title,
      LocalDate startDate,
      LocalDate endDate,
      TravelStatus status,
      Boolean hasHeavyBaggage,
      Boolean hasPets,
      TravelStyle travelStyle,
      Boolean preferFlatTerrain,
      TravelPace pace,
      Integer companionCount,
      String preferredFoods,
      CompanionType companionTypes,
      String accommodationArea) {
    this.title = title;
    this.startDate = startDate;
    this.endDate = endDate;
    this.status = status;
    this.hasHeavyBaggage = hasHeavyBaggage;
    this.hasPets = hasPets;
    this.travelStyle = travelStyle;
    this.preferFlatTerrain = preferFlatTerrain;
    this.pace = pace;
    this.companionCount = companionCount;
    this.preferredFoods = preferredFoods;
    this.companionTypes = companionTypes;
    this.accommodationArea = accommodationArea;
  }

  public void changeStatus(TravelStatus status) {
    this.status = status;
  }
}
