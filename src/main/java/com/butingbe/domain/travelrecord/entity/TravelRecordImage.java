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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "travel_record_image",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_travel_record_image_sequence",
          columnNames = {"travel_record_id", "sequence"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelRecordImage {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "travel_record_id", nullable = false)
  private TravelRecord travelRecord;

  @Column(nullable = false, length = 1000)
  private String url;

  @Column(nullable = false)
  private Integer sequence;

  @Builder
  public TravelRecordImage(TravelRecord travelRecord, String url, Integer sequence) {
    this.travelRecord = travelRecord;
    this.url = url;
    this.sequence = sequence;
  }
}
