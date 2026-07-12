package com.butingbe.domain.travelrecord.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "place_review",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_place_review_record_place",
          columnNames = {"travel_record_place_id"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceReview {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "travel_record_place_id", nullable = false)
  private TravelRecordPlace travelRecordPlace;

  @Column(nullable = false)
  private Integer rating;

  @Column(columnDefinition = "text")
  private String content;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Builder
  public PlaceReview(TravelRecordPlace travelRecordPlace, Integer rating, String content) {
    this.travelRecordPlace = travelRecordPlace;
    this.rating = rating;
    this.content = content;
  }

  public void update(Integer rating, String content) {
    if (rating != null) {
      this.rating = rating;
    }
    if (content != null) {
      this.content = content;
    }
  }
}
