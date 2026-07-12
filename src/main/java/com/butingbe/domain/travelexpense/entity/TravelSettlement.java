package com.butingbe.domain.travelexpense.entity;

import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.user.entity.User;
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
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "travel_settlement",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_travel_settlement_travel_id", columnNames = "travel_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelSettlement {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "travel_id", nullable = false, updatable = false)
  private Travel travel;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "confirmed_by_user_id", nullable = false, updatable = false)
  private User confirmedBy;

  @CreationTimestamp
  @Column(name = "confirmed_at", nullable = false, updatable = false)
  private LocalDateTime confirmedAt;

  @Builder
  public TravelSettlement(Travel travel, User confirmedBy) {
    this.travel = Objects.requireNonNull(travel, "Travel is required.");
    this.confirmedBy = Objects.requireNonNull(confirmedBy, "Settlement confirmer is required.");
  }
}
