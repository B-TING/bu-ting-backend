package com.butingbe.domain.zonetitle.dto.response;

import com.butingbe.domain.zonetitle.entity.CityGrade;
import java.time.OffsetDateTime;

/** 도시 등급 응답. next는 최고 등급이면 null. */
public record CityGradeResDto(
    String grade, String gradeName, OffsetDateTime reachedAt, NextGrade next) {

  public record NextGrade(String grade, String condition) {}

  public static CityGradeResDto of(CityGrade grade, OffsetDateTime reachedAt) {
    CityGrade next = grade.next();
    return new CityGradeResDto(
        grade.name(),
        grade.gradeName(),
        reachedAt,
        next == null ? null : new NextGrade(next.name(), grade.nextCondition()));
  }
}
