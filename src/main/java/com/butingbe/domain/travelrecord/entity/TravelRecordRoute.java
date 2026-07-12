package com.butingbe.domain.travelrecord.entity;

import com.butingbe.domain.travel.entity.PlaceProvider;
import com.butingbe.domain.travel.entity.TransportType;
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
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "travel_record_route",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_travel_record_route_from_to",
          columnNames = {"travel_record_day_id", "from_travel_record_place_id", "to_travel_record_place_id"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelRecordRoute {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "travel_record_day_id", nullable = false)
  private TravelRecordDay travelRecordDay;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "from_travel_record_place_id", nullable = false)
  private TravelRecordPlace fromPlace;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "to_travel_record_place_id", nullable = false)
  private TravelRecordPlace toPlace;

  @Enumerated(EnumType.STRING)
  @Column(name = "transport_type", nullable = false, length = 30)
  private TransportType transportType;

  @Column(name = "duration_minutes")
  private Integer durationMinutes;

  @Column(name = "distance_meters")
  private Integer distanceMeters;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private PlaceProvider provider;

  @Column(name = "calculated_at")
  private OffsetDateTime calculatedAt;

  @Builder
  public TravelRecordRoute(
      TravelRecordDay travelRecordDay,
      TravelRecordPlace fromPlace,
      TravelRecordPlace toPlace,
      TransportType transportType,
      Integer durationMinutes,
      Integer distanceMeters,
      PlaceProvider provider,
      OffsetDateTime calculatedAt) {
    this.travelRecordDay = travelRecordDay;
    this.fromPlace = fromPlace;
    this.toPlace = toPlace;
    this.transportType = transportType;
    this.durationMinutes = durationMinutes;
    this.distanceMeters = distanceMeters;
    this.provider = provider;
    this.calculatedAt = calculatedAt;
  }
}
