package com.butingbe.domain.travelrecord.entity;

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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "travel_record_like",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_travel_record_like_user_record",
          columnNames = {"user_id", "travel_record_id"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelRecordLike {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "travel_record_id", nullable = false)
  private TravelRecord travelRecord;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Builder
  public TravelRecordLike(User user, TravelRecord travelRecord) {
    this.user = user;
    this.travelRecord = travelRecord;
  }
}
