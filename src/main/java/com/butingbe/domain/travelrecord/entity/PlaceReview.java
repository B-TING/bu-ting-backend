package com.butingbe.domain.travelrecord.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

  @ElementCollection
  @CollectionTable(
      name = "place_review_tag",
      joinColumns = @JoinColumn(name = "place_review_id", nullable = false))
  @OrderColumn(name = "sequence")
  @Column(name = "tag", nullable = false, length = 30)
  private List<String> tags = new ArrayList<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Builder
  public PlaceReview(
      TravelRecordPlace travelRecordPlace, Integer rating, String content, List<String> tags) {
    this.travelRecordPlace = travelRecordPlace;
    this.rating = rating;
    this.content = content;
    updateTags(tags);
  }

  public void update(Integer rating, String content, List<String> tags) {
    if (rating != null) {
      this.rating = rating;
    }
    if (content != null) {
      this.content = content;
    }
    if (tags != null) {
      updateTags(tags);
    }
  }

  private void updateTags(List<String> tags) {
    this.tags.clear();
    if (tags != null) {
      this.tags.addAll(tags);
    }
  }
}
