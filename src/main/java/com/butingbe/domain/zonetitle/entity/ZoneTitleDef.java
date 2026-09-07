package com.butingbe.domain.zonetitle.entity;

import com.butingbe.global.common.TimestampEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 구역별 칭호 정의(tier 1/2/3). 6구역 × 3단 = 18개를 시드한다. */
@Entity
@Table(name = "zone_title_def")
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
}
