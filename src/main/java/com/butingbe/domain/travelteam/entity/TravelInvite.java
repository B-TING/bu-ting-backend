package com.butingbe.domain.travelteam.entity;

import com.butingbe.domain.travel.entity.Travel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "travel_invite")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelInvite {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "travel_id", nullable = false)
  private Travel travel;

  @Column(nullable = false, unique = true, length = 100)
  private String token;

  @Column(nullable = false)
  private Boolean used = false;

  @Column(name = "expired_at", nullable = false)
  private OffsetDateTime expiredAt;

  @Builder
  public TravelInvite(Travel travel, String token, OffsetDateTime expiredAt) {
    this.travel = travel;
    this.token = token;
    this.expiredAt = expiredAt;
  }

  public boolean isExpired() {
    return OffsetDateTime.now().isAfter(this.expiredAt);
  }

  public void markUsed() {
    this.used = true;
  }
}
