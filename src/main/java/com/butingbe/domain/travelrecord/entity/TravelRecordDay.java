package com.butingbe.domain.travelrecord.entity;

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
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "travel_record_day",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_travel_record_day_number",
          columnNames = {"travel_record_id", "day_number"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelRecordDay {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "travel_record_id", nullable = false)
  private TravelRecord travelRecord;

  @Column(name = "original_plan_id")
  private UUID originalPlanId;

  @Column(name = "day_number", nullable = false)
  private Integer dayNumber;

  @Column(name = "visit_date", nullable = false)
  private LocalDate visitDate;

  @Builder
  public TravelRecordDay(
      TravelRecord travelRecord, UUID originalPlanId, Integer dayNumber, LocalDate visitDate) {
    this.travelRecord = travelRecord;
    this.originalPlanId = originalPlanId;
    this.dayNumber = dayNumber;
    this.visitDate = visitDate;
  }
}
