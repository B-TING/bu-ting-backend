package com.butingbe.domain.zonetitle.entity;

import com.butingbe.global.common.TimestampEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 구역별 칭호 정의(tier 1/2/3). 6구역 × 3단 = 18개를 시드한다. */
@Entity
@Table(
    name = "zone_title_def",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_zone_title_def_zone_tier",
          columnNames = {"zone_id", "tier"}),
      @UniqueConstraint(
          name = "uk_zone_title_def_zone_required_count",
          columnNames = {"zone_id", "required_success_count"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneTitleDef extends TimestampEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "title_def_id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "title_code", nullable = false, length = 50)
  private String titleCode;

  @Column(name = "zone_id", nullable = false, length = 30)
  private String zoneId;

  @Column(nullable = false)
  private Integer tier;

  @Column(name = "required_success_count", nullable = false)
  private Integer requiredSuccessCount;

  @Column(name = "title_name", nullable = false, length = 100)
  private String titleName;

  @Column(nullable = false, length = 20)
  private String style;

  @Column(nullable = false, length = 20)
  private String color;

  @Version
  @Column(nullable = false)
  private Long revision;

  @Builder
  private ZoneTitleDef(
      String titleCode,
      String zoneId,
      Integer tier,
      Integer requiredSuccessCount,
      String titleName,
      String style,
      String color) {
    this.titleCode = titleCode;
    this.zoneId = zoneId;
    this.tier = tier;
    this.requiredSuccessCount = requiredSuccessCount;
    this.titleName = titleName;
    this.style = style;
    this.color = color;
  }

  /** 이름·달성 기준을 수정한다. null은 건너뛴다. */
  public void applyEditable(String titleName, Integer requiredSuccessCount) {
    if (titleName != null) {
      this.titleName = titleName;
    }
    if (requiredSuccessCount != null) {
      this.requiredSuccessCount = requiredSuccessCount;
    }
  }
}
