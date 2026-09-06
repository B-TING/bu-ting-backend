package com.butingbe.domain.zonetitle.entity;

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
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 유저가 보유한 구역 칭호. 유저·정의당 하나(UK), 대표 칭호는 유저당 하나(부분 UK). */
@Entity
@Table(
    name = "user_zone_title",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_user_zone_title",
          columnNames = {"user_id", "title_def_id"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserZoneTitle {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "user_title_id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "title_def_id", nullable = false)
  private ZoneTitleDef titleDef;

  @Column(name = "zone_id", nullable = false, length = 30)
  private String zoneId;

  @Column(nullable = false)
  private Boolean equipped;

  @Column(name = "earned_at", nullable = false)
  private OffsetDateTime earnedAt;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  @Builder
  private UserZoneTitle(UUID userId, ZoneTitleDef titleDef, String zoneId, boolean equipped) {
    this.userId = userId;
    this.titleDef = titleDef;
    this.zoneId = zoneId;
    this.equipped = equipped;
    this.earnedAt = OffsetDateTime.now();
  }

  public void equip() {
    this.equipped = true;
  }

  public void unequip() {
    this.equipped = false;
  }

  public void revoke(OffsetDateTime at) {
    if (revokedAt == null) {
      revokedAt = at;
    }
  }
}
