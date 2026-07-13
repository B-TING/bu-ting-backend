package com.butingbe.domain.travelrecord.entity;

import com.butingbe.domain.travel.entity.Travel;
import com.butingbe.domain.user.entity.User;
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
import java.time.LocalDate;
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
    name = "travel_record",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_travel_record_travel_author",
          columnNames = {"original_travel_id", "author_id"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "original_travel_id")
  private Travel originalTravel;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "author_id", nullable = false)
  private User author;

  @Column(nullable = false, length = 100)
  private String title;

  @Column(columnDefinition = "text")
  private String content;

  @Column(name = "cover_image_url", length = 1000)
  private String coverImageUrl;

  @Column(name = "travel_start_date", nullable = false)
  private LocalDate travelStartDate;

  @Column(name = "travel_end_date", nullable = false)
  private LocalDate travelEndDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TravelRecordStatus status;

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Builder
  public TravelRecord(
      Travel originalTravel,
      User author,
      String title,
      String content,
      String coverImageUrl,
      LocalDate travelStartDate,
      LocalDate travelEndDate,
      TravelRecordStatus status,
      LocalDateTime publishedAt) {
    this.originalTravel = originalTravel;
    this.author = author;
    this.title = title;
    this.content = content;
    this.coverImageUrl = coverImageUrl;
    this.travelStartDate = travelStartDate;
    this.travelEndDate = travelEndDate;
    this.status = status != null ? status : TravelRecordStatus.DRAFT;
    this.publishedAt = publishedAt;
  }

  public void updateContent(String title, String content, String coverImageUrl) {
    if (title != null) {
      this.title = title;
    }
    if (content != null) {
      this.content = content;
    }
    if (coverImageUrl != null) {
      this.coverImageUrl = coverImageUrl;
    }
  }

  public void publish(LocalDateTime publishedAt) {
    this.status = TravelRecordStatus.PUBLISHED;
    this.publishedAt = publishedAt;
  }

  public void hide() {
    this.status = TravelRecordStatus.HIDDEN;
  }

  public void republish() {
    this.status = TravelRecordStatus.PUBLISHED;
  }
}
