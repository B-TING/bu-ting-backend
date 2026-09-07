package com.butingbe.domain.zonetitle.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 도시 등급이 오른 시점 기록. */
@Entity
@Table(name = "user_city_grade_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCityGradeHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CityGrade grade;

  @Column(name = "reached_at", nullable = false)
  private OffsetDateTime reachedAt;

  @Builder
  private UserCityGradeHistory(UUID userId, CityGrade grade) {
    this.userId = userId;
    this.grade = grade;
    this.reachedAt = OffsetDateTime.now();
  }
}
