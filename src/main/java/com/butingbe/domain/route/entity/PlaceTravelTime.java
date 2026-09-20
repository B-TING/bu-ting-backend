package com.butingbe.domain.route.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 외부 경로 API가 준 한 구간의 소요 시간·거리. 좌표 계산 결과는 담지 않는다. */
@Entity
@Table(name = "place_travel_time")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceTravelTime {

  @EmbeddedId private PlaceTravelTimeId id;

  @Column(name = "duration_minutes", nullable = false)
  private int durationMinutes;

  @Column(name = "distance_meters", nullable = false)
  private int distanceMeters;

  @Column(name = "fetched_at", nullable = false)
  private LocalDateTime fetchedAt;

  public PlaceTravelTime(
      PlaceTravelTimeId id, int durationMinutes, int distanceMeters, LocalDateTime fetchedAt) {
    this.id = id;
    this.durationMinutes = durationMinutes;
    this.distanceMeters = distanceMeters;
    this.fetchedAt = fetchedAt;
  }

  /** 새로 받아온 값으로 갱신한다. */
  public void refresh(int durationMinutes, int distanceMeters, LocalDateTime fetchedAt) {
    this.durationMinutes = durationMinutes;
    this.distanceMeters = distanceMeters;
    this.fetchedAt = fetchedAt;
  }

  public boolean freshAt(LocalDateTime threshold) {
    return fetchedAt.isAfter(threshold);
  }
}
