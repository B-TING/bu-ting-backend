package com.butingbe.domain.storage.entity;

import com.butingbe.domain.station.entity.Station;
import com.butingbe.global.common.TimestampEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "locker_location")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StorageLocation extends TimestampEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "location_detail", columnDefinition = "text")
  private String locationDetail;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "station_id", nullable = false)
  private Station station;

  @Column(name = "small_count", nullable = false)
  private Integer smallCount;

  @Column(name = "medium_count", nullable = false)
  private Integer mediumCount;

  @Column(name = "large_count", nullable = false)
  private Integer largeCount;

  @Column(name = "extra_large_count", nullable = false)
  private Integer extraLargeCount;

  @Column(name = "raw_fee_text", columnDefinition = "text")
  private String costRaw;

  @Column(length = 100)
  private String company;

  @OneToMany(mappedBy = "lockerLocation", fetch = FetchType.LAZY)
  private List<LockerFee> fees;

  @Builder
  public StorageLocation(
      UUID id,
      Station station,
      String locationDetail,
      Integer smallCount,
      Integer mediumCount,
      Integer largeCount,
      Integer extraLargeCount,
      String costRaw,
      String company) {
    this.id = id;
    this.station = station;
    this.locationDetail = locationDetail;
    this.smallCount = smallCount;
    this.mediumCount = mediumCount;
    this.largeCount = largeCount;
    this.extraLargeCount = extraLargeCount;
    this.costRaw = costRaw;
    this.company = company;
  }
}
