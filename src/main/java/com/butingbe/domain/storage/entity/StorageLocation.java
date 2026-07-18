package com.butingbe.domain.storage.entity;

import com.butingbe.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
public class StorageLocation extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "external_id", nullable = false, length = 100)
  private String externalId;

  @Column(name = "source_type", nullable = false, length = 50)
  private String sourceType;

  @Column(name = "subway_line", nullable = false)
  private Integer line;

  @Column(name = "name", nullable = false, length = 100)
  private String stationName;

  @Column(name = "location_detail", columnDefinition = "text")
  private String locationDetail;

  @Column(nullable = false)
  private Double latitude;

  @Column(nullable = false)
  private Double longitude;

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
      String externalId,
      String sourceType,
      Integer line,
      String stationName,
      String locationDetail,
      Double latitude,
      Double longitude,
      Integer smallCount,
      Integer mediumCount,
      Integer largeCount,
      Integer extraLargeCount,
      String costRaw,
      String company) {
    this.id = id;
    this.externalId = externalId;
    this.sourceType = sourceType;
    this.line = line;
    this.stationName = stationName;
    this.locationDetail = locationDetail;
    this.latitude = latitude;
    this.longitude = longitude;
    this.smallCount = smallCount;
    this.mediumCount = mediumCount;
    this.largeCount = largeCount;
    this.extraLargeCount = extraLargeCount;
    this.costRaw = costRaw;
    this.company = company;
  }
}
