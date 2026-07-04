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
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "plan")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Plan {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "travel_id", nullable = false)
  private Travel travel;

  @Column(name = "day_number", nullable = false)
  private Integer dayNumber;

  @Column(name = "visit_date")
  private LocalDate visitDate;

  @Builder
  public Plan(Travel travel, Integer dayNumber, LocalDate visitDate) {
    this.travel = travel;
    this.dayNumber = dayNumber;
    this.visitDate = visitDate;
  }
}
