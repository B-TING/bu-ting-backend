package com.butingbe.domain.travelsurvey.entity;

import com.butingbe.domain.travelsurvey.dto.request.TravelSurveyProfileReqDto;
import com.butingbe.domain.user.entity.User;
import com.butingbe.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "travel_survey")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelSurvey extends BaseEntity {

  @Id
  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "preferred_language", nullable = false, length = 5)
  private String preferredLanguage;

  @Column(name = "is_planned")
  private Boolean planned;

  @Column(name = "is_relaxed")
  private Boolean relaxed;

  @Column(name = "is_solo")
  private Boolean solo;

  @Column(name = "is_light")
  private Boolean light;

  @Column(name = "is_familiar")
  private Boolean familiar;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "purposes", columnDefinition = "varchar(30)[]")
  private String[] purposes;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "skipped_steps", nullable = false, columnDefinition = "integer[]")
  private Integer[] skippedSteps;

  @Column(name = "skipped_all", nullable = false)
  private boolean skippedAll;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "ai_prompt_context", columnDefinition = "text")
  private String aiPromptContext;

  @Builder
  public TravelSurvey(User user, TravelSurveyProfileReqDto request) {
    this.user = user;
    update(request);
  }

  public void update(TravelSurveyProfileReqDto request) {
    this.preferredLanguage = request.normalizedPreferredLanguage();
    this.purposes = request.purposesArray();
    this.skippedSteps = request.skippedStepsArray();
    this.skippedAll = request.skippedAll();
    this.completedAt = LocalDateTime.now();

    if (request.skippedAll()) {
      this.planned = null;
      this.relaxed = null;
      this.solo = null;
      this.light = null;
      this.familiar = null;
    } else {
      this.planned = request.isPlanned();
      this.relaxed = request.isRelaxed();
      this.solo = request.isSolo();
      this.light = request.isLight();
      this.familiar = request.isFamiliar();
    }

    this.aiPromptContext = buildPromptContext();
  }

  private String buildPromptContext() {
    if (skippedAll) {
      return "preferred_language=%s; skipped_all=true".formatted(preferredLanguage);
    }

    return "preferred_language=%s; purposes=%s; planned=%s; relaxed=%s; solo=%s; light=%s; familiar=%s"
        .formatted(
            preferredLanguage, String.join(",", purposes), planned, relaxed, solo, light, familiar);
  }
}
